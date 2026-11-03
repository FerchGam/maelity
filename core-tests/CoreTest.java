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
        System.out.println(fails == 0 ? "CORE TESTS OK" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
