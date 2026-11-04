package dev.maelity.client;

import dev.maelity.MaelityConfig;
import dev.maelity.MaelityMod;
import dev.maelity.ai.Personality;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Apariencia elegida por el jugador (forma, cara y colores por humor), leida de la configuracion del cliente. */
public final class MaelityLook {
    private static final Map<String, ResourceLocation> FACES = new HashMap<>();
    private static final java.util.Set<String> STYLES = java.util.Set.of("classic", "cute", "cat", "robot", "minimal");
    private static final int[] DEFAULTS = {0xFFD60A, 0xFF8FD8, 0x7FD4A8, 0x3D8BFF, 0x8A5CD6, 0xE8402A};

    public static boolean ball() {
        try { return MaelityConfig.CLIENT.shape.get() == dev.maelity.Options.Shape.BALL; } catch (Exception e) { return false; }
    }

    public static String faceStyle() {
        try {
            String s = MaelityConfig.CLIENT.faceStyle.get().id();
            return STYLES.contains(s) ? s : "classic";
        } catch (Exception e) {
            return "classic";
        }
    }

    public static ResourceLocation faceTexture(Personality p) {
        String key = faceStyle() + "_" + p.id;
        return FACES.computeIfAbsent(key,
                k -> ResourceLocation.fromNamespaceAndPath(MaelityMod.MODID, "textures/entity/face/" + k + ".png"));
    }

    /** Color RGB (0xRRGGBB) para ese humor. */
    public static int colorFor(Personality p) {
        try {
            if (!MaelityConfig.CLIENT.moodColors.get()) return MaelityConfig.CLIENT.singleColor.get().rgb;
            var v = MaelityConfig.CLIENT.moodColor.get(p.id);
            return v == null ? DEFAULTS[p.ordinal()] : v.get().rgb;
        } catch (Exception e) {
            return DEFAULTS[p.ordinal() % DEFAULTS.length];
        }
    }

    static int parse(String hex, int fallback) {
        if (hex == null) return fallback;
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (!h.matches("[0-9a-fA-F]{6}")) return fallback;
        return Integer.parseInt(h, 16);
    }

    private MaelityLook() {}
}
