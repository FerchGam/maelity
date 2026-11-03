package dev.maelity.core;

import java.util.Locale;
import java.util.Set;

/**
 * Decide si Maelity regala un objeto, segun su humor, su carino por el jugador, lo valioso del objeto
 * y cuanto hace que regalo algo de ese nivel. Java puro (se prueba sin Minecraft).
 */
public final class GiftPolicy {
    public enum Tier { COMMON, RARE, EPIC, BANNED }

    /** reason: ok | prank | angry | cooldown | too_valuable | banned | disabled */
    public record Decision(boolean allowed, String itemId, int count, String reason) {}

    /** Cooldown por nivel, en ticks (20 ticks = 1 s). */
    public static final long[] COOLDOWN = {20L * 20, 20L * 60 * 5, 20L * 60 * 30, Long.MAX_VALUE};

    private static final Set<String> BANNED = Set.of(
            "air", "command_block", "chain_command_block", "repeating_command_block", "command_block_minecart",
            "structure_block", "structure_void", "jigsaw", "barrier", "light", "debug_stick", "bedrock",
            "end_portal_frame", "spawner", "trial_spawner", "vault", "knowledge_book", "reinforced_deepslate",
            "petrified_oak_slab", "farmland", "dirt_path", "frogspawn", "budding_amethyst", "end_gateway", "end_portal",
            "nether_portal");
    private static final Set<String> EPIC = Set.of(
            "elytra", "totem_of_undying", "enchanted_golden_apple", "nether_star", "beacon", "dragon_egg", "dragon_head",
            "heavy_core", "mace", "trident", "conduit", "heart_of_the_sea", "end_crystal", "diamond_block",
            "wither_skeleton_skull", "sniffer_egg", "netherite_upgrade_smithing_template");
    private static final Set<String> RARE = Set.of(
            "diamond", "emerald", "emerald_block", "gold_block", "iron_block", "golden_apple", "ender_pearl", "ender_eye",
            "blaze_rod", "experience_bottle", "enchanted_book", "name_tag", "saddle", "ancient_debris", "netherite_scrap",
            "echo_shard", "recovery_compass", "breeze_rod", "trial_key", "ominous_trial_key", "ghast_tear", "shulker_shell");
    /** Cosas que ayudan a sobrevivir: la protectora las da con mas facilidad si estas en peligro. */
    private static final Set<String> SAFETY = Set.of(
            "totem_of_undying", "golden_apple", "enchanted_golden_apple", "cooked_beef", "bread", "cooked_porkchop",
            "golden_carrot", "shield", "torch", "cooked_chicken", "baked_potato", "milk_bucket", "water_bucket");
    private static final String[] PRANKS = {"minecraft:poisonous_potato", "minecraft:dead_bush", "minecraft:rotten_flesh"};

    /** "Diamond" -> "minecraft:diamond". */
    public static String normalizeId(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (s.isEmpty()) return "";
        return s.indexOf(':') < 0 ? "minecraft:" + s : s;
    }

    private static String path(String id) {
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(i + 1);
    }

    public static Tier tierOf(String rawId) {
        String id = normalizeId(rawId);
        String p = path(id);
        if (p.isEmpty() || BANNED.contains(p) || p.endsWith("_spawn_egg")) return Tier.BANNED;
        if (!id.startsWith("minecraft:")) return Tier.RARE;                 // objetos de otros mods: con cuidado
        if (EPIC.contains(p) || p.startsWith("netherite_") || p.contains("shulker_box")) return Tier.EPIC;
        if (RARE.contains(p) || p.startsWith("diamond_") || p.startsWith("music_disc_") || p.endsWith("_horse_armor")
                || p.endsWith("_smithing_template") || p.endsWith("_pottery_sherd")) return Tier.RARE;
        return Tier.COMMON;
    }

    public static boolean isSafetyItem(String rawId) { return SAFETY.contains(path(normalizeId(rawId))); }

    /**
     * @param cooldownLeft ticks que faltan para poder regalar otra vez algo de cada nivel (COMMON, RARE, EPIC)
     * @param rnd          numero aleatorio 0..1 (para bromas); inyectado para poder probarlo
     */
    public static Decision decide(String mood, float anger, float affection, String rawId, int requested,
                                  Tier configMax, boolean playerInDanger, long[] cooldownLeft, double rnd) {
        String id = normalizeId(rawId);
        Tier t = tierOf(id);
        if (t == Tier.BANNED) return new Decision(false, id, 0, "banned");
        if (configMax == null) configMax = Tier.RARE;
        if (t.ordinal() > configMax.ordinal()) return new Decision(false, id, 0, "too_valuable");
        String m = mood == null ? "friendly" : mood;
        boolean safety = isSafetyItem(id);

        if ("angry".equals(m) && !(playerInDanger && safety && anger < 85f)) return new Decision(false, id, 0, "angry");

        Tier moodMax = switch (m) {
            case "friendly" -> affection >= 75f ? Tier.EPIC : Tier.RARE;
            case "playful" -> Tier.RARE;
            case "protective" -> (playerInDanger && safety) ? Tier.EPIC : Tier.RARE;
            case "mysterious" -> affection >= 40f ? Tier.RARE : Tier.COMMON;
            default -> Tier.COMMON;                                           // sarcastic, angry (en peligro)
        };
        if (t.ordinal() > moodMax.ordinal()) return new Decision(false, id, 0, "too_valuable");
        if (cooldownLeft != null && t.ordinal() < cooldownLeft.length && cooldownLeft[t.ordinal()] > 0
                && !(playerInDanger && safety && t == Tier.COMMON)) {
            return new Decision(false, id, 0, "cooldown");
        }

        int cap = switch (t) {
            case COMMON -> "sarcastic".equals(m) ? 8 : 32;
            case RARE -> "friendly".equals(m) && affection >= 60f ? 3 : 1;
            default -> 1;
        };
        int n = Math.max(1, Math.min(cap, requested <= 0 ? 1 : requested));

        if ("playful".equals(m) && !(playerInDanger && safety) && rnd < 0.25) {
            return new Decision(true, PRANKS[(int) (rnd * 4 * PRANKS.length) % PRANKS.length], 1, "prank");
        }
        return new Decision(true, id, n, "ok");
    }

    private GiftPolicy() {}
}
