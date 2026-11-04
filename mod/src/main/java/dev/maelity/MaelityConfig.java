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
        public final ModConfigSpec.EnumValue<Options.GiftTier> giftsMaxTier;
        public final ModConfigSpec.BooleanValue defendEnabled;
        public final ModConfigSpec.BooleanValue proactiveComments;
        public final ModConfigSpec.BooleanValue glowAtNight;
        public final ModConfigSpec.EnumValue<Options.FollowStyle> followStyle;
        public final ModConfigSpec.IntValue playerAge;
        public final ModConfigSpec.BooleanValue allowProfanity;
        public final ModConfigSpec.DoubleValue followDistance;
        public final ModConfigSpec.DoubleValue followSpeed;
        public final ModConfigSpec.BooleanValue autoMoveAside;

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
            giftsMaxTier = b.comment("Lo mas valioso que puede regalar. ADMIN = todo, incluso bloques de comandos y huevos de spawn.")
                    .translation(T + "giftsMaxTier")
                    .defineEnum("giftsMaxTier", Options.GiftTier.ADMIN);
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
            b.translation(T + "movement").push("movement");
            followStyle = b.comment("Como te sigue: CALM (tranquila: si estas quieto se queda quieta) | BEHIND (detras de ti)")
                    .translation(T + "followStyle")
                    .defineEnum("followStyle", Options.FollowStyle.CALM);
            followDistance = b.comment("A cuantos bloques de ti se queda (mas = mas lejos y menos estorba).")
                    .translation(T + "followDistance")
                    .defineInRange("followDistance", 3.5, 2.0, 10.0);
            followSpeed = b.comment("Velocidad al seguirte (0.5 lenta .. 2.0 rapida).")
                    .translation(T + "followSpeed")
                    .defineInRange("followSpeed", 1.0, 0.5, 2.0);
            autoMoveAside = b.comment("Se aparta sola si la miras un rato con un bloque en la mano. Si no, solo cuando le dices 'muevete'.")
                    .translation(T + "autoMoveAside")
                    .define("autoMoveAside", false);
            b.pop();
            b.translation(T + "language_filter").push("language_filter");
            playerAge = b.comment("Tu edad (0 = sin poner). Al ponerla por primera vez queda guardada; si es menor de 18,"
                            + " las groserias quedan bloqueadas para siempre en esta instalacion.")
                    .translation(T + "playerAge")
                    .defineInRange("playerAge", 0, 0, 120);
            allowProfanity = b.comment("Groserias cuando esta muy enfadada (solo mayores de 18). Apagado = solo insultos suaves como 'inutil'.")
                    .translation(T + "allowProfanity")
                    .define("allowProfanity", false);
            b.pop();
        }
    }

    /** Solo cliente: lo unico que el jugador elige es el idioma. */
    public static final class Client {
        public final ModConfigSpec.EnumValue<Options.Lang> language;
        public final ModConfigSpec.BooleanValue languageChosen;
        public final ModConfigSpec.BooleanValue voiceEnabled;
        public final ModConfigSpec.DoubleValue volume;
        public final ModConfigSpec.EnumValue<Options.VoiceEngine> voiceEngine;
        public final ModConfigSpec.EnumValue<Options.Gender> voiceGender;
        public final ModConfigSpec.EnumValue<Options.PiperVoiceEs> piperVoiceEs;
        public final ModConfigSpec.EnumValue<Options.PiperVoiceEn> piperVoiceEn;
        public final ModConfigSpec.ConfigValue<String> elevenApiKey;
        public final ModConfigSpec.ConfigValue<String> elevenVoiceId;
        public final ModConfigSpec.ConfigValue<String> fishApiKey;
        public final ModConfigSpec.ConfigValue<String> fishVoiceId;
        public final ModConfigSpec.EnumValue<Options.FishModel> fishModel;
        public final ModConfigSpec.EnumValue<Options.Shape> shape;
        public final ModConfigSpec.EnumValue<Options.FaceStyle> faceStyle;
        public final ModConfigSpec.BooleanValue moodColors;
        public final ModConfigSpec.EnumValue<Options.Color> singleColor;
        public final java.util.Map<String, ModConfigSpec.EnumValue<Options.Color>> moodColor = new java.util.LinkedHashMap<>();

        Client(ModConfigSpec.Builder b) {
            Options.Lang sys = Locale.getDefault().getLanguage().equals("es") ? Options.Lang.ES : Options.Lang.EN;
            language = b.comment("Idioma de Maelity (voz y charla)")
                    .translation(T + "language")
                    .defineEnum("language", sys);
            languageChosen = b.define("languageChosen", false);
            voiceEnabled = b.comment("Reproducir la voz de Maelity.")
                    .translation(T + "voiceEnabled")
                    .define("voiceEnabled", true);
            volume = b.translation(T + "volume").defineInRange("volume", 0.9, 0.0, 1.0);

            b.translation(T + "voice").push("voice");
            voiceEngine = b.comment("Motor de voz: PIPER (gratis, sin internet, se descarga solo la 1a vez) | ELEVENLABS | FISH | WINDOWS")
                    .translation(T + "voiceEngine")
                    .defineEnum("voiceEngine", Options.VoiceEngine.PIPER);
            voiceGender = b.comment("Voz de mujer o de hombre")
                    .translation(T + "voiceGender")
                    .defineEnum("voiceGender", Options.Gender.FEMALE);
            piperVoiceEs = b.comment("Voz de Piper en espanol. AUTO = segun el genero elegido.")
                    .translation(T + "piperVoiceEs").defineEnum("piperVoiceEs", Options.PiperVoiceEs.AUTO);
            piperVoiceEn = b.comment("Voz de Piper en ingles. AUTO = segun el genero elegido.")
                    .translation(T + "piperVoiceEn").defineEnum("piperVoiceEn", Options.PiperVoiceEn.AUTO);
            elevenApiKey = b.comment("Clave de ElevenLabs (elevenlabs.io > API keys). Plan gratis con creditos mensuales.")
                    .translation(T + "elevenApiKey").define("elevenApiKey", "");
            elevenVoiceId = b.comment("Opcional: ID de voz de ElevenLabs. Vacio = automatica segun genero.")
                    .translation(T + "elevenVoiceId").define("elevenVoiceId", "");
            fishApiKey = b.comment("Clave de Fish Audio (fish.audio > API).")
                    .translation(T + "fishApiKey").define("fishApiKey", "");
            fishVoiceId = b.comment("Opcional: ID de voz de Fish Audio (de su biblioteca de voces). Vacio = voz por defecto.")
                    .translation(T + "fishVoiceId").define("fishVoiceId", "");
            fishModel = b.comment("Modelo de Fish Audio. FREE (s2.1-pro-free) es gratis por tiempo limitado; PRO (s2.1-pro) es de pago.")
                    .translation(T + "fishModel").defineEnum("fishModel", Options.FishModel.FREE);
            b.pop();

            b.translation(T + "look").push("look");
            shape = b.comment("Forma de Maelity: CUBE (cubo) | BALL (pelota redonda)")
                    .translation(T + "shape")
                    .defineEnum("shape", Options.Shape.CUBE);
            faceStyle = b.comment("Estilo de cara")
                    .translation(T + "faceStyle")
                    .defineEnum("faceStyle", Options.FaceStyle.CLASSIC);
            moodColors = b.comment("El color cambia segun el humor. Si esta apagado, usa el color fijo siempre.")
                    .translation(T + "moodColors")
                    .define("moodColors", true);
            singleColor = b.comment("Color fijo cuando el color no cambia con el humor.")
                    .translation(T + "singleColor")
                    .defineEnum("singleColor", Options.Color.YELLOW);
            Object[][] defaults = {{"friendly", Options.Color.YELLOW}, {"playful", Options.Color.PINK}, {"sarcastic", Options.Color.MINT},
                    {"protective", Options.Color.BLUE}, {"mysterious", Options.Color.PURPLE}, {"angry", Options.Color.RED}};
            for (Object[] d : defaults) {
                moodColor.put((String) d[0], b.comment("Color cuando esta " + d[0])
                        .translation(T + "color_" + d[0])
                        .defineEnum("color_" + d[0], (Options.Color) d[1]));
            }
            b.pop();
        }
    }

    private MaelityConfig() {}
}
