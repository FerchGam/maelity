package dev.maelity;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MaelityConfig {
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final Common COMMON;
    public static final Client CLIENT;

    private static final String T = "maelity.configuration.";

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        COMMON = new Common(b);
        COMMON_SPEC = b.build();
        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        CLIENT = new Client(c);
        CLIENT_SPEC = c.build();
    }

    /** Servidor (o mundo local): comportamiento de Maelity y buscador. */
    public static final class Common {
        public final ModConfigSpec.ConfigValue<String> groqApiKey;
        public final ModConfigSpec.BooleanValue hitEnabled;
        public final ModConfigSpec.DoubleValue hitDamage;
        public final ModConfigSpec.DoubleValue minHealthAfterHit;
        public final ModConfigSpec.IntValue maxHitsPerMinute;
        public final ModConfigSpec.BooleanValue revealCoordinates;
        public final ModConfigSpec.IntValue scanRadiusChunks;
        public final ModConfigSpec.IntValue chunksPerTick;

        Common(ModConfigSpec.Builder b) {
            b.translation(T + "ai").push("ai");
            groqApiKey = b.comment("Tu clave GRATIS de Groq (console.groq.com). Empieza con gsk_")
                    .translation(T + "groqApiKey")
                    .define("groqApiKey", "");
            b.pop();
            b.translation(T + "combat").push("combat");
            hitEnabled = b.comment("Permite que Maelity golpee al jugador cuando se enfada o se lo piden.")
                    .translation(T + "hitEnabled")
                    .define("hitEnabled", true);
            hitDamage = b.comment("Dano por golpe (2.0 = 1 corazon).")
                    .translation(T + "hitDamage")
                    .defineInRange("hitDamage", 2.0, 0.0, 6.0);
            minHealthAfterHit = b.comment("Maelity NUNCA te deja por debajo de esta vida (2.0 = 1 corazon). Un golpe que pasaria de ahi se omite.")
                    .translation(T + "minHealthAfterHit")
                    .defineInRange("minHealthAfterHit", 4.0, 1.0, 20.0);
            maxHitsPerMinute = b.translation(T + "maxHitsPerMinute")
                    .defineInRange("maxHitsPerMinute", 3, 1, 20);
            b.pop();
            b.translation(T + "finder").push("finder");
            revealCoordinates = b.comment("Muestra las coordenadas exactas en el chat al encontrar algo.")
                    .translation(T + "revealCoordinates")
                    .define("revealCoordinates", true);
            scanRadiusChunks = b.comment("Radio (en chunks) para buscar minerales/madera usando la semilla real del mundo.")
                    .translation(T + "scanRadiusChunks")
                    .defineInRange("scanRadiusChunks", 8, 2, 24);
            chunksPerTick = b.comment("Chunks generados/escaneados por tick. Mas = mas rapido pero mas lag.")
                    .translation(T + "chunksPerTick")
                    .defineInRange("chunksPerTick", 1, 1, 4);
            b.pop();
        }
    }

    /** Solo cliente: lo unico que el jugador elige es el idioma. */
    public static final class Client {
        public final ModConfigSpec.ConfigValue<String> language;
        public final ModConfigSpec.BooleanValue languageChosen;
        public final ModConfigSpec.BooleanValue voiceEnabled;
        public final ModConfigSpec.DoubleValue volume;

        Client(ModConfigSpec.Builder b) {
            String sys = Locale.getDefault().getLanguage().equals("es") ? "es" : "en";
            language = b.comment("Idioma de Maelity (voz y charla): es | en")
                    .translation(T + "language")
                    .defineInList("language", sys, new ArrayList<>(List.of("es", "en")));
            languageChosen = b.define("languageChosen", false);
            voiceEnabled = b.comment("Reproducir la voz de Maelity.")
                    .translation(T + "voiceEnabled")
                    .define("voiceEnabled", true);
            volume = b.translation(T + "volume").defineInRange("volume", 0.9, 0.0, 1.0);
        }
    }

    private MaelityConfig() {}
}
