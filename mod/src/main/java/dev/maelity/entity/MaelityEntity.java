package dev.maelity.entity;

import dev.maelity.MaelityConfig;
import dev.maelity.ModEntities;
import dev.maelity.ai.Gifts;
import dev.maelity.ai.MaelityBrain;
import dev.maelity.ai.MoodEngine;
import dev.maelity.ai.Personality;
import dev.maelity.core.CombatPolicy;
import dev.maelity.core.GiftPolicy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/** Maelity: companera voladora con IA. Sigue a su dueno, guia, conversa y, si hace falta, golpea (con limites). */
public class MaelityEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> DATA_MOOD =
            SynchedEntityData.defineId(MaelityEntity.class, EntityDataSerializers.INT);

    private final MoodEngine mood = new MoodEngine();
    private final Deque<String[]> history = new ArrayDeque<>();
    private UUID ownerId;
    private String lang = "en";
    private boolean stay;
    private boolean pendingHit;
    private BlockPos guideTarget;
    private volatile boolean thinking;
    private int hitsThisMinute;
    private long hitWindowStart;
    private long lastPetTick;

    // --- regalos
    private final long[] giftLast = {Long.MIN_VALUE / 4, Long.MIN_VALUE / 4, Long.MIN_VALUE / 4};

    // --- memoria de lo que ha pasado (para que la IA tenga contexto)
    public record GameEvent(long tick, String text) {}
    private final Deque<GameEvent> recentEvents = new ArrayDeque<>();

    // --- combate
    @Nullable private LivingEntity combatTarget;
    private long fleeUntil;
    private Vec3 fleeFrom = Vec3.ZERO;
    private CombatPolicy.Choice lastChoice = CombatPolicy.Choice.IGNORE;
    private long lastCombatTalk = Long.MIN_VALUE / 4;
    private int threatCount;
    private long lastThreatTick = Long.MIN_VALUE / 4;
    private String threatSummary = "";

    // --- comentarios por su cuenta
    private final Map<String, Long> proactiveLast = new HashMap<>();
    private long lastProactive = Long.MIN_VALUE / 4;
    private long prevDayTime = -1;

    public MaelityEntity(EntityType<? extends MaelityEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.7)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new MaelityFleeGoal(this));
        this.goalSelector.addGoal(1, new MaelityDefendGoal(this));
        this.goalSelector.addGoal(2, new MaelityHitGoal(this));
        this.goalSelector.addGoal(3, new MaelityGuideGoal(this));
        this.goalSelector.addGoal(4, new MaelityFollowGoal(this, 1.15, 3.5f, 6f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MOOD, 0);
    }

    // ------------------------------------------------------------------ vuelo (igual que Allay)

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isControlledByLocalInstance()) {
            if (this.isInWater()) {
                this.moveRelative(0.02F, travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.8F));
            } else if (this.isInLava()) {
                this.moveRelative(0.02F, travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
            } else {
                this.moveRelative(this.getSpeed(), travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.91F));
            }
        }
        this.calculateEntityAnimation(false);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) { }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public boolean requiresCustomPersistence() { return true; }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING) || super.isInvulnerableTo(source);
    }

    // ------------------------------------------------------------------ ciclo de vida

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData d = super.finalizeSpawn(level, difficulty, reason, data);
        if (ownerId == null) {
            Player p = level.getNearestPlayer(this, 16);
            if (p != null) {
                ownerId = p.getUUID();
                if (p instanceof ServerPlayer sp) greet(sp);
            }
        }
        return d;
    }

    public void greet(ServerPlayer sp) {
        String l = clientLang(sp);
        this.lang = l;
        boolean es = "es".equals(l);
        MaelityBrain.event(sp, this,
                "You were just summoned and are meeting the player for the first time. Introduce yourself briefly "
                        + "and tell them to hold the V key to talk to you.",
                es ? "¡Hola! Soy Maelity. Mantén la tecla V y háblame." : "Hi! I'm Maelity. Hold the V key and talk to me.");
    }

    public static String clientLang(ServerPlayer sp) {
        String l = sp.clientInformation().language();
        return l != null && l.toLowerCase().startsWith("es") ? "es" : "en";
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        long now = level().getGameTime();
        if (now % 10 == 5 && ownerId != null && level() instanceof ServerLevel sl0) {
            ServerPlayer o = sl0.getServer().getPlayerList().getPlayer(ownerId);
            if (o != null && o.isAlive()) scanCombat(o, now);
        }
        if (now % 200 == 40 && ownerId != null && level() instanceof ServerLevel sl1) {
            ServerPlayer o = sl1.getServer().getPlayerList().getPlayer(ownerId);
            if (o != null && o.isAlive()) proactive(o, now);
        }
        if (now % 20 == 0) {
            if (!stay && ownerId != null && level() instanceof ServerLevel sl) {
                ServerPlayer sp = sl.getServer().getPlayerList().getPlayer(ownerId);
                if (sp != null && sp.serverLevel() != sl) {
                    followAcrossDimension(sp);
                    return;
                }
            }
            Player owner = getOwner();
            mood.tick(level(), owner, now);
            syncMood();
            if (now % 100 == 0 && getHealth() < getMaxHealth()) heal(1.0f);
        }
    }

    /**
     * Copia el estado de la mood al dato sincronizado (de ahi lee el cliente la textura). Se hace SIEMPRE:
     * los cambios que propone el modelo (suggest) ocurren fuera de tick() y antes no llegaban al cliente.
     */
    private void syncMood() {
        int o = mood.get().ordinal();
        if (entityData.get(DATA_MOOD) != o) entityData.set(DATA_MOOD, o);
    }

    /** El dueno cambio de dimension: Maelity no puede cruzar sola, asi que reaparece a su lado con el mismo estado. */
    private void followAcrossDimension(ServerPlayer sp) {
        ServerLevel dest = sp.serverLevel();
        MaelityEntity copy = ModEntities.MAELITY.get().create(dest);
        if (copy == null) return;
        copy.moveTo(sp.getX(), sp.getY() + 1.5, sp.getZ(), sp.getYRot(), 0f);
        copy.ownerId = ownerId;
        copy.lang = lang;
        copy.mood.load(mood.get(), mood.anger(), mood.affection());
        System.arraycopy(giftLast, 0, copy.giftLast, 0, giftLast.length);
        for (GameEvent ev : recentEvents) copy.recentEvents.addLast(ev);
        copy.entityData.set(DATA_MOOD, mood.get().ordinal());
        for (String[] h : history) copy.history.addLast(h);
        this.discard();
        dest.addFreshEntity(copy);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Maelity no muere en combate: antes de caer, huye (solo /kill y similares la matan)
        if (!level().isClientSide && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && getHealth() - amount < 2f) {
            amount = Math.max(0f, getHealth() - 2f);
            fleeUntil = level().getGameTime() + 120;
            fleeFrom = source.getEntity() != null ? source.getEntity().position() : position();
            combatTarget = null;
            if (amount <= 0f) return false;
        }
        boolean r = super.hurt(source, amount);
        if (r && !level().isClientSide && source.getEntity() instanceof Player p && p.getUUID().equals(ownerId)) {
            mood.onHurtByOwner(level().getGameTime());
            entityData.set(DATA_MOOD, mood.get().ordinal());
            if (p instanceof ServerPlayer sp && !isThinking()) {
                boolean es = "es".equals(lang);
                MaelityBrain.event(sp, this, "The player just hit you.",
                        es ? "¡Ey! ¿Por qué me pegas?" : "Hey! Why did you hit me?");
            }
        }
        return r;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide || hand != InteractionHand.MAIN_HAND) return InteractionResult.sidedSuccess(level().isClientSide);
        if (ownerId == null) ownerId = player.getUUID();
        if (player.getUUID().equals(ownerId) && player instanceof ServerPlayer sp) {
            long now = level().getGameTime();
            if (now - lastPetTick > 60) {
                lastPetTick = now;
                stay = !stay;
                boolean es = "es".equals(lang);
                MaelityBrain.event(sp, this,
                        stay ? "The player poked you and told you to wait here." : "The player poked you; you now follow them again.",
                        stay ? (es ? "Me quedo aquí." : "I'll wait here.") : (es ? "¡Vamos!" : "Let's go!"));
            }
        }
        return InteractionResult.CONSUME;
    }

    // ------------------------------------------------------------------ golpe (con salvaguardas)

    /**
     * @param playerAsked true si el jugador lo pidio explicitamente.
     * @return si se acepto la orden de golpear.
     */
    public boolean requestHit(boolean playerAsked) {
        if (!MaelityConfig.COMMON.hitEnabled.get()) return false;
        if (!playerAsked && !(mood.get() == Personality.ANGRY && mood.anger() >= 70f)) return false;
        long now = level().getGameTime();
        if (now - hitWindowStart > 1200) { hitWindowStart = now; hitsThisMinute = 0; }
        if (hitsThisMinute >= MaelityConfig.COMMON.maxHitsPerMinute.get()) return false;
        Player o = getOwner();
        if (o == null || o.getHealth() - MaelityConfig.COMMON.hitDamage.get() < MaelityConfig.COMMON.minHealthAfterHit.get()) return false;
        pendingHit = true;
        return true;
    }

    boolean hasPendingHit() { return pendingHit; }
    void clearPendingHit() { pendingHit = false; }

    /** Ejecuta el golpe si sigue siendo seguro. */
    void performHit(Player target) {
        pendingHit = false;
        float dmg = MaelityConfig.COMMON.hitDamage.get().floatValue();
        if (target.getHealth() - dmg < MaelityConfig.COMMON.minHealthAfterHit.get()) return;
        hitsThisMinute++;
        swing(InteractionHand.MAIN_HAND);
        target.hurt(damageSources().mobAttack(this), dmg);
        playSound(SoundEvents.PLAYER_ATTACK_WEAK, 1.0f, 1.2f);
    }


    // ------------------------------------------------------------------ combate: defender, huir o avisar

    private static boolean isBoss(Mob m) {
        return m instanceof Warden || m instanceof WitherBoss || m instanceof EnderDragon || m instanceof ElderGuardian;
    }

    private static boolean isThreatTo(Mob m, Player owner) {
        if (m.getTarget() == owner) return true;
        return owner.getLastHurtByMob() == m && owner.tickCount - owner.getLastHurtByMobTimestamp() < 100;
    }

    private void scanCombat(ServerPlayer owner, long now) {
        if (owner.level() != level() || distanceToSqr(owner) > 32 * 32) { combatTarget = null; return; }
        AABB box = owner.getBoundingBox().inflate(14);
        List<Mob> threats = level().getEntitiesOfClass(Mob.class, box,
                m -> m.isAlive() && !(m instanceof MaelityEntity) && isThreatTo(m, owner));
        Vec3 creeperPos = null;
        for (Creeper c : level().getEntitiesOfClass(Creeper.class, owner.getBoundingBox().inflate(7))) {
            if (c.getSwellDir() > 0) { creeperPos = c.position(); break; }
        }
        boolean boss = !level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(24), MaelityEntity::isBoss).isEmpty();

        threatCount = threats.size();
        if (threatCount > 0 || creeperPos != null || boss) lastThreatTick = now;
        Map<String, Integer> kinds = new LinkedHashMap<>();
        for (Mob m : threats) kinds.merge(BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getPath(), 1, Integer::sum);
        StringBuilder sum = new StringBuilder();
        kinds.forEach((k, v) -> sum.append(sum.length() == 0 ? "" : ", ").append(v).append(' ').append(k));
        threatSummary = sum.toString();

        CombatPolicy.Choice c = CombatPolicy.decide(mood.get().id, mood.anger(), getHealth() / getMaxHealth(),
                owner.getHealth() / owner.getMaxHealth(), threatCount, creeperPos != null, boss,
                MaelityConfig.COMMON.defendEnabled.get());
        if (isFleeing() && c == CombatPolicy.Choice.DEFEND && getHealth() / getMaxHealth() < 0.5f) c = CombatPolicy.Choice.FLEE;

        switch (c) {
            case DEFEND -> {
                if (combatTarget == null || !combatTarget.isAlive() || !threats.contains(combatTarget)) {
                    combatTarget = threats.stream().min(Comparator.comparingDouble(m -> m.distanceToSqr(owner))).orElse(null);
                }
            }
            case FLEE -> {
                combatTarget = null;
                fleeUntil = now + 100;
                if (creeperPos != null) fleeFrom = creeperPos;
                else if (!threats.isEmpty()) fleeFrom = centroid(threats);
                else fleeFrom = owner.position();
            }
            default -> combatTarget = null;
        }

        boolean danger = threatCount > 0 || creeperPos != null || boss;
        if (!danger) { lastChoice = CombatPolicy.Choice.IGNORE; return; }
        if ((c != lastChoice || now - lastCombatTalk > 20 * 45) && now - lastCombatTalk > 20 * 12 && !isThinking()) {
            lastCombatTalk = now;
            narrateCombat(owner, c, creeperPos != null, boss);
        }
        lastChoice = c;
    }

    private static Vec3 centroid(List<Mob> mobs) {
        double x = 0, y = 0, z = 0;
        for (Mob m : mobs) { x += m.getX(); y += m.getY(); z += m.getZ(); }
        int n = Math.max(1, mobs.size());
        return new Vec3(x / n, y / n, z / n);
    }

    private void narrateCombat(ServerPlayer owner, CombatPolicy.Choice c, boolean creeper, boolean boss) {
        boolean es = "es".equals(lang);
        String who = threatSummary.isEmpty() ? "monsters" : threatSummary;
        String hp = Math.round(owner.getHealth()) + "/" + Math.round(owner.getMaxHealth());
        String note;
        String fb;
        if (creeper) {
            note = "A creeper is about to EXPLODE next to the player! You are flying away. Warn the player to run, one urgent line.";
            fb = es ? "¡Creeper! ¡Corre!" : "Creeper! Run!";
        } else if (boss) {
            note = "A very dangerous boss mob is near the player. You decided to keep your distance and FLEE. Warn the player.";
            fb = es ? "¡Eso es demasiado para mí, aléjate!" : "That's way too much for me, get away!";
        } else {
            switch (c) {
                case DEFEND -> {
                    note = "DANGER: the player (health " + hp + ") is being attacked by " + who + ". You decided to DEFEND them and are flying in to fight. One short line in character.";
                    fb = es ? "¡Yo te cubro!" : "I've got your back!";
                }
                case FLEE -> {
                    note = "The player (health " + hp + ") is attacked by " + who + ". It is too much for you (or you are hurt), so you decided to FLEE and keep your distance. Tell the player, in character.";
                    fb = es ? "¡Son demasiados, me aparto!" : "Too many of them, I'm backing off!";
                }
                case IGNORE -> {
                    note = "The player (health " + hp + ") is attacked by " + who + ", but you are angry at them and decided NOT to help this time. Say it in character (sharp, not cruel).";
                    fb = es ? "Arréglatelas tú solo." : "Handle it yourself.";
                }
                default -> {
                    note = "The player (health " + hp + ") is attacked by " + who + ". Defending is disabled, so just WARN them.";
                    fb = es ? "¡Cuidado, te atacan!" : "Watch out, you're under attack!";
                }
            }
        }
        recordEvent("player attacked by " + who + "; you chose to " + c.name().toLowerCase());
        MaelityBrain.event(owner, this, note, fb);
    }

    /** Orden del jugador o del modelo: "ataca a ese zombie". */
    public boolean commandDefend() {
        Player o = getOwner();
        if (o == null || !MaelityConfig.COMMON.defendEnabled.get()) return false;
        combatTarget = level().getEntitiesOfClass(Monster.class, o.getBoundingBox().inflate(16), Monster::isAlive).stream()
                .min(Comparator.comparingDouble(m -> m.distanceToSqr(o))).orElse(null);
        return combatTarget != null;
    }

    /** Orden: "huye" / "alejate". */
    public void commandFlee() {
        Player o = getOwner();
        Monster near = o == null ? null : level().getEntitiesOfClass(Monster.class, o.getBoundingBox().inflate(16), Monster::isAlive)
                .stream().min(Comparator.comparingDouble(m -> m.distanceToSqr(this))).orElse(null);
        fleeFrom = near != null ? near.position() : (o != null ? o.position() : position());
        fleeUntil = level().getGameTime() + 100;
        combatTarget = null;
    }

    void onDefeated(LivingEntity target) {
        if (combatTarget == target) combatTarget = null;
        recordEvent("you defeated a " + BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath() + " that threatened the player");
        mood.addAffection(1f);
    }

    @Nullable LivingEntity getCombatTarget() { return combatTarget; }
    void clearCombatTarget() { combatTarget = null; }
    boolean isFleeing() { return level().getGameTime() < fleeUntil; }
    Vec3 fleeFrom() { return fleeFrom; }

    public String combatState() {
        if (isFleeing()) return "fleeing";
        if (combatTarget != null) return "defending the player";
        return level().getGameTime() - lastThreatTick < 100 ? "alert" : "calm";
    }

    public String threatSummary() { return level().getGameTime() - lastThreatTick < 100 ? threatSummary : ""; }

    /** El jugador esta en peligro (vida baja o atacado ahora mismo). */
    public boolean ownerInDanger() {
        Player o = getOwner();
        if (o == null) return false;
        return o.getHealth() <= 8f || level().getGameTime() - lastThreatTick < 100;
    }

    // ------------------------------------------------------------------ regalos

    public long[] giftCooldownLeft(long now) {
        long[] left = new long[giftLast.length];
        for (int i = 0; i < giftLast.length; i++) left[i] = Math.max(0L, giftLast[i] + GiftPolicy.COOLDOWN[i] - now);
        return left;
    }

    public void markGift(GiftPolicy.Tier t, long now) {
        if (t.ordinal() < giftLast.length) giftLast[t.ordinal()] = now;
    }

    // ------------------------------------------------------------------ memoria de eventos

    public void recordEvent(String text) {
        recentEvents.addLast(new GameEvent(level().getGameTime(), text));
        while (recentEvents.size() > 8) recentEvents.removeFirst();
    }

    public Iterable<GameEvent> recentEvents() { return recentEvents; }

    // ------------------------------------------------------------------ comentarios por su cuenta

    private void proactive(ServerPlayer owner, long now) {
        long day = level().getDayTime() % 24000L;
        boolean nightStarted = prevDayTime >= 0 && prevDayTime < 13000L && day >= 13000L;
        prevDayTime = day;
        if (!MaelityConfig.COMMON.proactiveComments.get() || isThinking()) return;
        if (owner.level() != level() || distanceToSqr(owner) > 24 * 24) return;
        if (now - lastProactive < 20 * 60 || now - lastCombatTalk < 20 * 20 || now - mood.lastTalk() < 20 * 20) return;

        boolean es = "es".equals(lang);
        String m = mood.get().id;
        boolean kind = m.equals("friendly") || m.equals("protective") || m.equals("playful");
        int food = owner.getFoodData().getFoodLevel();
        String key = null, note = null, fb = null;

        if (nightStarted && ready("night", now, 20 * 60 * 8)) {
            key = "night";
            note = "Night just started; hostile mobs will spawn. Give the player a short useful tip in character.";
            fb = es ? "Se hace de noche... ten cuidado." : "Night is falling... be careful.";
        } else if (food <= 6 && ready("hungry", now, 20 * 60 * 4)) {
            key = "hungry";
            Gifts.Result g = kind ? Gifts.give(owner, this, "minecraft:bread", 4) : null;
            if (g != null && g.given()) {
                note = "The player was hungry (food " + food + "/20), so you just GAVE them " + g.count() + "x " + g.itemName() + " on your own. Tell them, in character.";
                fb = es ? "Toma, come algo." : "Here, eat something.";
            } else {
                note = "The player is hungry (food " + food + "/20). Remind them to eat, in character.";
                fb = es ? "Tienes hambre, come algo." : "You're hungry, eat something.";
            }
        } else if (owner.getHealth() <= 8f && threatCount == 0 && ready("hurt", now, 20 * 60 * 4)) {
            key = "hurt";
            Gifts.Result g = m.equals("protective") || (m.equals("friendly") && mood.affection() >= 60f)
                    ? Gifts.give(owner, this, "minecraft:golden_apple", 1) : null;
            if (g != null && g.given()) {
                note = "The player is badly hurt (health " + Math.round(owner.getHealth()) + "/20), so you GAVE them a " + g.itemName() + ". Tell them to eat it.";
                fb = es ? "Toma esta manzana dorada, cómetela ya." : "Take this golden apple, eat it now.";
            } else {
                note = "The player is badly hurt (health " + Math.round(owner.getHealth()) + "/20) but safe now. Advise them to heal, in character.";
                fb = es ? "Estás muy herido, descansa." : "You're badly hurt, rest a bit.";
            }
        } else if (owner.getY() < 0 && level().getMaxLocalRawBrightness(owner.blockPosition()) < 4 && ready("dark", now, 20 * 60 * 6)) {
            key = "dark";
            Gifts.Result g = kind ? Gifts.give(owner, this, "minecraft:torch", 16) : null;
            note = g != null && g.given()
                    ? "The player is deep underground in the dark, so you GAVE them " + g.count() + " torches. Tell them, in character."
                    : "The player is deep underground in the dark. Warn them about mobs and suggest torches, in character.";
            fb = es ? "Aquí abajo está muy oscuro..." : "It's really dark down here...";
        } else if (now - mood.lastTalk() > 20 * 60 * 6 && getRandom().nextFloat() < 0.15f && ready("idle", now, 20 * 60 * 6)) {
            key = "idle";
            note = "The player has been quiet for a while. Say something spontaneous in character about what they are doing or the place you are in (use WORLD STATE).";
            fb = es ? "Qué silencio..." : "So quiet...";
        }
        if (key == null) return;
        proactiveLast.put(key, now);
        lastProactive = now;
        MaelityBrain.event(owner, this, note, fb);
    }

    private boolean ready(String key, long now, long cooldown) {
        Long last = proactiveLast.get(key);
        return last == null || now - last > cooldown;
    }

    // ------------------------------------------------------------------ accesores

    public MoodEngine mood() { return mood; }
    public Personality getPersonality() { return Personality.fromOrdinal(entityData.get(DATA_MOOD)); }
    public UUID getOwnerId() { return ownerId; }
    public void setOwner(UUID id) { this.ownerId = id; }
    @Nullable public Player getOwner() { return ownerId == null ? null : level().getPlayerByUUID(ownerId); }
    public boolean isStay() { return stay; }
    public void setStay(boolean s) { this.stay = s; if (s) guideTarget = null; }
    public String lang() { return lang; }
    public void setLang(String l) { this.lang = "es".equals(l) ? "es" : "en"; }
    public boolean isThinking() { return thinking; }
    public void setThinking(boolean t) { this.thinking = t; }
    @Nullable public BlockPos getGuideTarget() { return guideTarget; }
    public void setGuideTarget(@Nullable BlockPos p) { this.guideTarget = p; if (p != null) stay = false; }

    public void forceMood(Personality p) {
        mood.force(p, level().getGameTime(), 20 * 30);
        entityData.set(DATA_MOOD, mood.get().ordinal());
    }

    public Iterable<String[]> history() { return history; }

    public void remember(String role, String content) {
        history.addLast(new String[]{role, content});
        while (history.size() > 12) history.removeFirst();
    }

    /** La Maelity (propia) mas cercana al jugador, o null. */
    @Nullable
    public static MaelityEntity findFor(ServerPlayer p) {
        ServerLevel lvl = p.serverLevel();
        List<MaelityEntity> list = lvl.getEntitiesOfClass(MaelityEntity.class, p.getBoundingBox().inflate(48),
                a -> p.getUUID().equals(a.ownerId));
        return list.stream().min(Comparator.comparingDouble(a -> a.distanceToSqr(p))).orElse(null);
    }

    // ------------------------------------------------------------------ guardado

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putString("Mood", mood.get().id);
        tag.putFloat("Anger", mood.anger());
        tag.putBoolean("Stay", stay);
        tag.putString("Lang", lang);
        tag.putFloat("Affection", mood.affection());
        tag.putLongArray("GiftTicks", giftLast.clone());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        mood.load(Personality.fromId(tag.getString("Mood")), tag.getFloat("Anger"),
                tag.contains("Affection") ? tag.getFloat("Affection") : 50f);
        long[] g = tag.getLongArray("GiftTicks");
        for (int i = 0; i < Math.min(g.length, giftLast.length); i++) giftLast[i] = g[i];
        stay = tag.getBoolean("Stay");
        if (tag.contains("Lang")) lang = tag.getString("Lang");
        entityData.set(DATA_MOOD, mood.get().ordinal());
    }

    // ------------------------------------------------------------------ sonidos discretos

    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.ALLAY_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ALLAY_DEATH; }
}
