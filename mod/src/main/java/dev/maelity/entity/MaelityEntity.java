package dev.maelity.entity;

import dev.maelity.MaelityConfig;
import dev.maelity.ModEntities;
import dev.maelity.ai.MaelityBrain;
import dev.maelity.ai.MoodEngine;
import dev.maelity.ai.Personality;
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
                .add(Attributes.ATTACK_DAMAGE, 2.0)
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
        this.goalSelector.addGoal(1, new MaelityHitGoal(this));
        this.goalSelector.addGoal(2, new MaelityGuideGoal(this));
        this.goalSelector.addGoal(3, new MaelityFollowGoal(this, 1.15, 3.5f, 6f));
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
        copy.mood.load(mood.get(), mood.anger());
        copy.entityData.set(DATA_MOOD, mood.get().ordinal());
        for (String[] h : history) copy.history.addLast(h);
        this.discard();
        dest.addFreshEntity(copy);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        mood.load(Personality.fromId(tag.getString("Mood")), tag.getFloat("Anger"));
        stay = tag.getBoolean("Stay");
        if (tag.contains("Lang")) lang = tag.getString("Lang");
        entityData.set(DATA_MOOD, mood.get().ordinal());
    }

    // ------------------------------------------------------------------ sonidos discretos

    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.ALLAY_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.ALLAY_DEATH; }
}
