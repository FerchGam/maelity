import dev.maelity.core.GiftPolicy;
import dev.maelity.core.CombatPolicy;
import dev.maelity.core.*;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;

/** Prueba la logica pura del mod (sin Minecraft). Uso: java CoreTest [http://backend:puerto token] */
public class CoreTest {
    static byte[] wavNormal(byte[] pcm) { return VoiceRecorder.toWav(pcm, 16000); }
    static int fails = 0;
    static void check(boolean ok, String name) { System.out.println((ok ? "  ok   " : "  FAIL ") + name); if (!ok) fails++; }

    public static void main(String[] a) throws Exception {
        // ---- intenciones
        var i = IntentParser.parse("¿Dónde puedo encontrar diamantes?");
        check(i.find() != null && i.find().id().equals("diamond"), "es: donde hay diamantes -> diamond");
        i = IntentParser.parse("I need iron, can you help me find some?");
        check(i.find() != null && i.find().id().equals("iron"), "en: need iron -> iron");
        i = IntentParser.parse("Where is the nearest jungle temple?");
        check(i.find() != null && i.find().id().equals("jungle_temple"), "en: jungle temple gana a jungle");
        i = IntentParser.parse("llévame a una aldea");
        check(i.find() != null && i.find().id().equals("village") && i.guide(), "es: llevame a una aldea -> village + guide");
        i = IntentParser.parse("busca oro del nether");
        check(i.find() != null && i.find().id().equals("nether_gold"), "es: oro del nether != oro");
        i = IntentParser.parse("encuentra escombros antiguos");
        check(i.find() != null && i.find().id().equals("ancient_debris"), "es: escombros antiguos");
        i = IntentParser.parse("me gusta mi espada de diamante");
        check(i.find() == null, "sin verbo de busqueda no dispara find");
        i = IntentParser.parse("Sígueme por favor");
        check(i.follow() && !i.stay(), "es: sigueme -> follow");
        i = IntentParser.parse("quédate aquí");
        check(i.stay() && !i.follow(), "es: quedate -> stay");
        i = IntentParser.parse("stop following me");
        check(i.stay(), "en: stop following -> stay");
        i = IntentParser.parse("hit me");
        check(i.hitMe(), "en: hit me");
        i = IntentParser.parse("pégame");
        check(i.hitMe(), "es: pegame");
        i = IntentParser.parse("hola, ¿cómo estás?");
        check(i.isEmpty(), "charla normal no dispara acciones");

        // ---- brujula
        check(Compass.direction(0, -50, "en").equals("north"), "compass: -z = north");
        check(Compass.direction(50, 0, "es").equals("este"), "compass: +x = este");
        check(Compass.direction(-30, 30, "es").equals("suroeste"), "compass: -x,+z = suroeste");

        // ---- WAV
        byte[] pcm = new byte[16000 * 2];
        for (int n = 0; n < 16000; n++) { short s = (short) (Math.sin(n * 2 * Math.PI * 440 / 16000) * 8000); pcm[2 * n] = (byte) s; pcm[2 * n + 1] = (byte) (s >> 8); }
        byte[] wav = VoiceRecorder.toWav(pcm, 16000);
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
            check(ais.getFormat().getSampleRate() == 16000f && ais.getFormat().getChannels() == 1 && ais.getFrameLength() == 16000,
                    "WAV valido: 16kHz mono, 1 s");
        }
        check(VoiceRecorder.rms(pcm) > 4000 && VoiceRecorder.rms(new byte[32000]) == 0, "RMS: tono vs silencio");
        check(BackendClient.jsonEscape("a\"b\n\\").equals("a\\\"b\\n\\\\"), "jsonEscape");

        // ---- regresiones de la auditoria
        i = IntentParser.parse("necesito una mesa de crafteo");
        check(i.find() == null, "'mesa' (mueble) ya no dispara la busqueda de badlands");
        i = IntentParser.parse("llevame a las tierras baldias");
        check(i.find() != null && i.find().id().equals("badlands"), "es: tierras baldias -> badlands");
        check(IntentParser.isInsult("eres una estúpida, cállate") && IntentParser.isInsult("you are useless"), "insultos es/en");
        check(!IntentParser.isInsult("hola, ¿cómo estás?") && !IntentParser.isInsult("me gusta esta aldea"), "charla normal no es insulto");
        check(IntentParser.isKind("Gracias, eres genial") && IntentParser.isKind("you're great, thanks"), "amabilidad es/en");
        byte[] streaming = VoiceRecorder.toWav(pcm, 16000);
        for (int k = 40; k < 44; k++) streaming[k] = (byte) 0xFF;      // data size = 0xFFFFFFFF (cabecera de streaming)
        try (AudioInputStream ais = VoicePlayer.open(streaming)) {
            check(ais.getFrameLength() == 16000 && ais.getFormat().getSampleRate() == 16000f,
                    "VoicePlayer.open lee WAV con cabecera de streaming (frames=" + ais.getFrameLength() + ")");
        }
        byte[] zeroSize = VoiceRecorder.toWav(pcm, 16000);
        for (int k = 40; k < 44; k++) zeroSize[k] = 0;                 // data size = 0
        try (AudioInputStream ais = VoicePlayer.open(zeroSize)) {
            check(ais.getFrameLength() == 16000, "VoicePlayer.open lee WAV con data size 0");
        }
        try (AudioInputStream ais = VoicePlayer.open(wavNormal(pcm))) {
            check(ais.getFrameLength() == 16000, "VoicePlayer.open sigue leyendo WAV normales");
        }

        // ---- cliente HTTP contra el backend real (opcional)
        if (a.length >= 2) {
            BackendClient c = new BackendClient(a[0], a[1]);
            String chat = c.chat("[{\"role\":\"user\",\"content\":\"hola\"}]", "es").get();
            check(chat.contains("content") && chat.contains("hola"), "HTTP chat via backend");
            String stt = c.stt(wav, "es").get();
            check(stt.contains("diamantes"), "HTTP stt via backend -> " + stt);
            byte[] audio = c.tts("Hola jugador", "es", "playful").get();
            try (AudioInputStream ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audio))) {
                check(ais.getFrameLength() > 0, "HTTP tts devuelve WAV decodificable por Java Sound");
            }
            try { new BackendClient(a[0], "mal").chat("[]", "es").get(); check(false, "token incorrecto rechazado"); }
            catch (Exception ex) { check(ex.getCause() instanceof BackendClient.BackendException b && b.status == 401, "token incorrecto -> 401"); }
        }


        // ---- aceptar que te guie
        check(IntentParser.isYes("sí"), "'sí' es afirmativo");
        check(IntentParser.isYes("Sí, llévame"), "'sí, llévame' es afirmativo");
        check(IntentParser.isYes("yes please"), "'yes please' es afirmativo");
        check(!IntentParser.isYes("no, todavía no"), "'no, todavía no' no es afirmativo");
        check(IntentParser.wantsGuide("guíame"), "'guíame' pide guía");
        check(!IntentParser.isYes("así está bien la casa"), "'así' no cuenta como 'sí'");
        // ---- regalos
        {
            long[] none = {0, 0, 0};
            check(GiftPolicy.tierOf("diamond") == GiftPolicy.Tier.RARE, "diamante = raro");
            check(GiftPolicy.tierOf("minecraft:netherite_sword") == GiftPolicy.Tier.EPIC, "espada de netherita = epico");
            check(GiftPolicy.tierOf("Bread") == GiftPolicy.Tier.COMMON, "pan = comun");
            check(GiftPolicy.tierOf("minecraft:command_block") == GiftPolicy.Tier.BANNED, "bloque de comandos prohibido");
            check(GiftPolicy.tierOf("zombie_spawn_egg") == GiftPolicy.Tier.BANNED, "huevos de spawn prohibidos");
            var d = GiftPolicy.decide("friendly", 0, 50, "bread", 5, GiftPolicy.Tier.EPIC, false, none, 0.9);
            check(d.allowed() && d.count() == 5 && d.itemId().equals("minecraft:bread"), "amigable regala pan");
            d = GiftPolicy.decide("angry", 80, 50, "bread", 5, GiftPolicy.Tier.EPIC, false, none, 0.9);
            check(!d.allowed() && d.reason().equals("angry"), "enfadada no regala");
            d = GiftPolicy.decide("friendly", 0, 50, "netherite_ingot", 1, GiftPolicy.Tier.EPIC, false, none, 0.9);
            check(!d.allowed() && d.reason().equals("too_valuable"), "sin carino alto no da netherita");
            d = GiftPolicy.decide("friendly", 0, 90, "netherite_ingot", 1, GiftPolicy.Tier.EPIC, false, none, 0.9);
            check(d.allowed(), "con mucho carino si da netherita");
            d = GiftPolicy.decide("friendly", 0, 90, "netherite_ingot", 1, GiftPolicy.Tier.RARE, false, none, 0.9);
            check(!d.allowed(), "la config limita el nivel maximo");
            d = GiftPolicy.decide("friendly", 0, 50, "diamond", 1, GiftPolicy.Tier.EPIC, false, new long[]{0, 500, 0}, 0.9);
            check(!d.allowed() && d.reason().equals("cooldown"), "espera entre regalos raros");
            d = GiftPolicy.decide("protective", 0, 20, "totem_of_undying", 1, GiftPolicy.Tier.EPIC, true, none, 0.9);
            check(d.allowed(), "protectora da totem si estas en peligro");
            d = GiftPolicy.decide("protective", 0, 20, "totem_of_undying", 1, GiftPolicy.Tier.EPIC, false, none, 0.9);
            check(!d.allowed(), "protectora no da totem sin peligro");
            d = GiftPolicy.decide("sarcastic", 0, 50, "diamond", 1, GiftPolicy.Tier.EPIC, false, none, 0.9);
            check(!d.allowed(), "sarcastica no da diamantes");
            d = GiftPolicy.decide("sarcastic", 0, 50, "cobblestone", 64, GiftPolicy.Tier.EPIC, false, none, 0.9);
            check(d.allowed() && d.count() == 8, "sarcastica da poco");
            d = GiftPolicy.decide("playful", 0, 50, "bread", 3, GiftPolicy.Tier.EPIC, false, none, 0.1);
            check(d.allowed() && d.reason().equals("prank") && !d.itemId().equals("minecraft:bread"), "juguetona a veces hace bromas");
            d = GiftPolicy.decide("angry", 50, 50, "golden_apple", 1, GiftPolicy.Tier.EPIC, true, none, 0.9);
            check(!d.allowed(), "enfadada: la manzana dorada (raro) sigue fuera de su alcance");
            d = GiftPolicy.decide("angry", 50, 50, "bread", 2, GiftPolicy.Tier.EPIC, true, none, 0.9);
            check(d.allowed(), "enfadada pero te ve en peligro: te da pan");
        }
        // ---- combate
        {
            check(CombatPolicy.decide("friendly", 0, 1f, 1f, 2, false, false, true) == CombatPolicy.Choice.DEFEND, "amigable defiende");
            check(CombatPolicy.decide("friendly", 0, 1f, 1f, 1, true, false, true) == CombatPolicy.Choice.FLEE, "creeper -> huye");
            check(CombatPolicy.decide("protective", 0, 1f, 1f, 1, false, true, true) == CombatPolicy.Choice.FLEE, "jefe -> huye");
            check(CombatPolicy.decide("friendly", 0, 0.2f, 1f, 1, false, false, true) == CombatPolicy.Choice.FLEE, "muy herida -> huye");
            check(CombatPolicy.decide("angry", 80, 1f, 0.9f, 1, false, false, true) == CombatPolicy.Choice.IGNORE, "enfadada te deja solo");
            check(CombatPolicy.decide("angry", 80, 1f, 0.3f, 1, false, false, true) == CombatPolicy.Choice.DEFEND, "enfadada te salva si casi mueres");
            check(CombatPolicy.decide("mysterious", 0, 1f, 1f, 4, false, false, true) == CombatPolicy.Choice.FLEE, "misteriosa huye de grupos");
            check(CombatPolicy.decide("friendly", 0, 1f, 1f, 7, false, false, true) == CombatPolicy.Choice.FLEE, "demasiados -> huye");
            check(CombatPolicy.decide("protective", 0, 1f, 1f, 7, false, false, true) == CombatPolicy.Choice.DEFEND, "protectora aguanta mas");
            check(CombatPolicy.decide("friendly", 0, 1f, 1f, 2, false, false, false) == CombatPolicy.Choice.WARN, "defensa desactivada -> avisa");
            check(CombatPolicy.decide("friendly", 0, 1f, 1f, 0, false, false, true) == CombatPolicy.Choice.IGNORE, "sin enemigos -> nada");
        }
        System.out.println(fails == 0 ? "CORE TESTS OK" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
