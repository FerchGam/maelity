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
        public final ModConfigSpec.BooleanValue giftsEnabled;
        public final ModConfigSpec.ConfigValue<String> giftsMaxTier;
        public final ModConfigSpec.BooleanValue defendEnabled;
        public final ModConfigSpec.BooleanValue proactiveComments;
        public final ModConfigSpec.BooleanValue glowAtNight;

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
            b.translation(T + "behavior").push("behavior");
            giftsEnabled = b.comment("Maelity puede regalarte objetos (segun su humor y su carino por ti).")
                    .translation(T + "giftsEnabled")
                    .define("giftsEnabled", true);
            giftsMaxTier = b.comment("Lo mas valioso que puede regalar: common | rare | epic")
                    .translation(T + "giftsMaxTier")
                    .defineInList("giftsMaxTier", "epic", new ArrayList<>(List.of("common", "rare", "epic")));
            defendEnabled = b.comment("Maelity te defiende de los monstruos (o huye, segun la situacion y su humor).")
                    .translation(T + "defendEnabled")
                    .define("defendEnabled", true);
            proactiveComments = b.comment("Maelity comenta por su cuenta (cae la noche, tienes hambre, estas herido...).")
                    .translation(T + "proactiveComments")
                    .define("proactiveComments", true);
            glowAtNight = b.comment("De noche o bajo tierra Maelity brilla e ilumina a su alrededor (bloque de luz invisible que la sigue).")
                    .translation(T + "glowAtNight")
                    .define("glowAtNight", true);
            b.pop();
        }
    }

    /** Solo cliente: lo unico que el jugador elige es el idioma. */
    public static final class Client {
        public final ModConfigSpec.ConfigValue<String> language;
        public final ModConfigSpec.BooleanValue languageChosen;
        public final ModConfigSpec.BooleanValue voiceEnabled;
        public final ModConfigSpec.DoubleValue volume;
        public final ModConfigSpec.ConfigValue<String> voiceEngine;
        public final ModConfigSpec.ConfigValue<String> voiceGender;
        public final ModConfigSpec.ConfigValue<String> piperVoiceEs;
        public final ModConfigSpec.ConfigValue<String> piperVoiceEn;
        public final ModConfigSpec.ConfigValue<String> elevenApiKey;
        public final ModConfigSpec.ConfigValue<String> elevenVoiceId;
        public final ModConfigSpec.ConfigValue<String> fishApiKey;
        public final ModConfigSpec.ConfigValue<String> fishVoiceId;
        public final ModConfigSpec.ConfigValue<String> fishModel;
        public final ModConfigSpec.ConfigValue<String> shape;
        public final ModConfigSpec.ConfigValue<String> faceStyle;
        public final ModConfigSpec.BooleanValue moodColors;
        public final ModConfigSpec.ConfigValue<String> singleColor;
        public final java.util.Map<String, ModConfigSpec.ConfigValue<String>> moodColor = new java.util.LinkedHashMap<>();

        private static final java.util.function.Predicate<Object> HEX =
                o -> o instanceof String s && s.trim().matches("#?[0-9a-fA-F]{6}");

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

            b.translation(T + "voice").push("voice");
            voiceEngine = b.comment("Motor de voz: piper (gratis, sin internet, se descarga solo la 1a vez) | elevenlabs | fish | windows")
                    .translation(T + "voiceEngine")
                    .defineInList("voiceEngine", "piper", new ArrayList<>(List.of("piper", "elevenlabs", "fish", "windows")));
            voiceGender = b.comment("Voz femenina o masculina: female | male")
                    .translation(T + "voiceGender")
                    .defineInList("voiceGender", "female", new ArrayList<>(List.of("female", "male")));
            piperVoiceEs = b.comment("Opcional: voz de Piper en espanol (ej. es_MX-claude-high). Vacio = automatica segun genero.")
                    .translation(T + "piperVoiceEs").define("piperVoiceEs", "");
            piperVoiceEn = b.comment("Opcional: voz de Piper en ingles (ej. en_US-amy-medium). Vacio = automatica segun genero.")
                    .translation(T + "piperVoiceEn").define("piperVoiceEn", "");
            elevenApiKey = b.comment("Clave de ElevenLabs (elevenlabs.io > API keys). Plan gratis con creditos mensuales.")
                    .translation(T + "elevenApiKey").define("elevenApiKey", "");
            elevenVoiceId = b.comment("Opcional: ID de voz de ElevenLabs. Vacio = automatica segun genero.")
                    .translation(T + "elevenVoiceId").define("elevenVoiceId", "");
            fishApiKey = b.comment("Clave de Fish Audio (fish.audio > API).")
                    .translation(T + "fishApiKey").define("fishApiKey", "");
            fishVoiceId = b.comment("Opcional: ID de voz de Fish Audio (de su biblioteca de voces). Vacio = voz por defecto.")
                    .translation(T + "fishVoiceId").define("fishVoiceId", "");
            fishModel = b.comment("Modelo de Fish Audio. s2.1-pro-free es gratis por tiempo limitado; luego usa s2.1-pro (de pago).")
                    .translation(T + "fishModel").define("fishModel", "s2.1-pro-free");
            b.pop();

            b.translation(T + "look").push("look");
            shape = b.comment("Forma de Maelity: cube (cubo) | ball (pelotita)")
                    .translation(T + "shape")
                    .defineInList("shape", "cube", new ArrayList<>(List.of("cube", "ball")));
            faceStyle = b.comment("Estilo de cara: classic | cute | cat | robot | minimal")
                    .translation(T + "faceStyle")
                    .defineInList("faceStyle", "classic", new ArrayList<>(List.of("classic", "cute", "cat", "robot", "minimal")));
            moodColors = b.comment("El color cambia segun el humor. Si es false, usa singleColor siempre.")
                    .translation(T + "moodColors")
                    .define("moodColors", true);
            singleColor = b.comment("Color fijo (formato #RRGGBB) cuando moodColors = false.")
                    .translation(T + "singleColor")
                    .define("singleColor", "#FFD60A", HEX);
            String[][] defaults = {{"friendly", "#FFD60A"}, {"playful", "#FF8FD8"}, {"sarcastic", "#7FD4A8"},
                    {"protective", "#3D8BFF"}, {"mysterious", "#8A5CD6"}, {"angry", "#E8402A"}};
            for (String[] d : defaults) {
                moodColor.put(d[0], b.comment("Color (#RRGGBB) cuando esta " + d[0])
                        .translation(T + "color_" + d[0])
                        .define("color_" + d[0], d[1], HEX));
            }
            b.pop();
        }
    }

    private MaelityConfig() {}
}
