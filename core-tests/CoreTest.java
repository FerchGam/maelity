import dev.maelity.core.GiftPolicy;
import dev.maelity.core.CombatPolicy;
import dev.maelity.core.*;
import java.net.http.HttpClient;

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




        // ---- edad y groserias
        {
            java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("age");
            java.nio.file.Path lock = dir.resolve("age.lock");
            check(!AgeGate.profanityAllowed(lock, 0, true), "sin edad no hay groserias");
            check(!AgeGate.profanityAllowed(lock, 12, true), "12 anos: bloqueado");
            check(!AgeGate.profanityAllowed(lock, 30, true), "despues de poner 12, cambiar a 30 no sirve");
            check(AgeGate.lockedAge(lock, 30) == 12, "la primera edad queda guardada");
            java.nio.file.Path lock2 = dir.resolve("age2.lock");
            check(AgeGate.profanityAllowed(lock2, 25, true), "25 anos y activado: permitido");
            check(!AgeGate.profanityAllowed(lock2, 25, false), "25 anos sin activar: no");
            check(LanguageFilter.censor("eres un pendejo, joder").equals("eres un p******, j****"), "tapa groserias");
            check(LanguageFilter.censor("Cabrón inútil").equals("C***** inútil"), "tapa con acentos y deja 'inútil'");
            check(LanguageFilter.censor("what the fuck").equals("what the f***"), "tapa en ingles");
            check(LanguageFilter.censor("computadora y escondite").equals("computadora y escondite"), "no tapa palabras normales");
        }
        // ---- memoria y voces
        check("mi casa esta junto al rio".equals(IntentParser.rememberThat("Recuerda que mi casa está junto al río")), "'recuerda que...' guarda el dato");
        check(IntentParser.rememberThat("¿recuerdas cuando casi muero?") == null, "'¿recuerdas...?' no es una orden de recordar");
        check("my base is near the desert".equals(IntentParser.rememberThat("Maelity, remember that my base is near the desert")), "'remember that...' en ingles");
        check(PiperTts.voiceUrl("es_MX-claude-high", ".onnx").equals(
                "https://huggingface.co/rhasspy/piper-voices/resolve/main/es/es_MX/claude/high/es_MX-claude-high.onnx"), "URL de voz Piper");
        check(PiperTts.voiceUrl("es_ES-mls_10246-low", ".onnx.json").endsWith("/es/es_ES/mls_10246/low/es_ES-mls_10246-low.onnx.json"), "URL de voz Piper con guion bajo");
        check(!PiperTts.validVoice("../../evil-x-high"), "voz Piper invalida rechazada");
        {
            byte[] pcmIn = new byte[4410 * 2];
            byte[] w = Wav.fromPcm16(pcmIn, 22050, 1);
            try (AudioInputStream ais = VoicePlayer.open(w)) {
                check(ais.getFormat().getSampleRate() == 22050f && ais.getFrameLength() == 4410, "PCM de ElevenLabs -> WAV reproducible");
            }
        }
        {
            java.nio.file.Path tmp = java.nio.file.Files.createTempDirectory("mz");
            java.nio.file.Path z = tmp.resolve("a.zip");
            try (var zo = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(z))) {
                zo.putNextEntry(new java.util.zip.ZipEntry("piper/piper.exe")); zo.write(1); zo.closeEntry();
            }
            java.lang.reflect.Method um = PiperTts.class.getDeclaredMethod("unzip", java.nio.file.Path.class, java.nio.file.Path.class);
            um.setAccessible(true);
            um.invoke(null, z, tmp.resolve("bin"));
            check(java.nio.file.Files.exists(tmp.resolve("bin/piper/piper.exe")), "unzip de Piper");
            java.nio.file.Path bad = tmp.resolve("b.zip");
            try (var zo = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(bad))) {
                zo.putNextEntry(new java.util.zip.ZipEntry("../escape.txt")); zo.write(1); zo.closeEntry();
            }
            boolean blocked = false;
            try { um.invoke(null, bad, tmp.resolve("bin2")); } catch (java.lang.reflect.InvocationTargetException ex) { blocked = true; }
            check(blocked && !java.nio.file.Files.exists(tmp.resolve("escape.txt")), "zip con rutas peligrosas bloqueado");
        }
        {
            // servidor falso para comprobar como se llama a ElevenLabs y Fish Audio
            var seen = new java.util.concurrent.ConcurrentHashMap<String, String>();
            var srv = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            srv.createContext("/", ex -> {
                String body = new String(ex.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                seen.put("path", ex.getRequestURI().toString());
                seen.put("xi", String.valueOf(ex.getRequestHeaders().getFirst("xi-api-key")));
                seen.put("auth", String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
                seen.put("model", String.valueOf(ex.getRequestHeaders().getFirst("model")));
                seen.put("body", body);
                byte[] out = ex.getRequestURI().getPath().contains("text-to-speech") ? new byte[2000] : Wav.fromPcm16(new byte[2000], 44100, 1);
                ex.sendResponseHeaders(200, out.length); ex.getResponseBody().write(out); ex.close();
            });
            srv.start();
            int port = srv.getAddress().getPort();
            // redirige los hosts reales al servidor falso con un proxy HTTP simple no es trivial: se prueba con un cliente que reescribe la URL
            HttpClient fake = new RewritingClient("http://127.0.0.1:" + port);
            byte[] w1 = CloudTts.elevenLabs(fake, "k1", "VOICE", "Hola \"amigo\"").get();
            check(Wav.isWav(w1) && seen.get("path").equals("/v1/text-to-speech/VOICE?output_format=pcm_22050")
                    && seen.get("xi").equals("k1") && seen.get("body").contains("eleven_flash_v2_5") && seen.get("body").contains("\\\"amigo\\\""),
                    "ElevenLabs: ruta, clave y cuerpo correctos");
            byte[] w2 = CloudTts.fishAudio(fake, "k2", "s2.1-pro-free", "", "Hola").get();
            check(Wav.isWav(w2) && seen.get("path").equals("/v1/tts") && seen.get("auth").equals("Bearer k2")
                    && seen.get("model").equals("s2.1-pro-free") && seen.get("body").contains("\"format\":\"wav\"") && !seen.get("body").contains("reference_id"),
                    "Fish Audio: ruta, clave, modelo y cuerpo correctos");
            CloudTts.fishAudio(fake, "k2", "", "abc123", "Hola").get();
            check(seen.get("body").contains("\"reference_id\":\"abc123\""), "Fish Audio: voz elegida");
            boolean failed = false;
            try { CloudTts.elevenLabs(fake, "", "V", "x").get(); } catch (Exception ex) { failed = true; }
            check(failed, "ElevenLabs sin clave falla (y el mod usa la voz de Windows)");
            srv.stop(0);
        }

        // ---- apartarse
        check(IntentParser.wantsAside("Muévete, quiero poner un bloque ahí"), "'muévete' la aparta");
        check(IntentParser.wantsAside("apártate por favor"), "'apártate' la aparta");
        check(IntentParser.wantsAside("get out of the way"), "'get out of the way' la aparta");
        check(!IntentParser.wantsAside("vamos a la aldea"), "frase normal no la aparta");
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
            check(GiftPolicy.tierOf("minecraft:command_block") == GiftPolicy.Tier.ADMIN, "bloque de comandos = admin");
            check(GiftPolicy.tierOf("zombie_spawn_egg") == GiftPolicy.Tier.ADMIN, "huevos de spawn = admin");
            check(GiftPolicy.tierOf("air") == GiftPolicy.Tier.BANNED, "aire no se regala");
            var d = GiftPolicy.decide("friendly", 0, 50, "bread", 5, GiftPolicy.Tier.EPIC, false, none, 0.9);
            check(d.allowed() && d.count() == 5 && d.itemId().equals("minecraft:bread"), "amigable regala pan");
            d = GiftPolicy.decide("angry", 90, 50, "bread", 5, GiftPolicy.Tier.EPIC, false, none, 0.9);
            check(!d.allowed() && d.reason().equals("angry"), "furiosa no regala");
            d = GiftPolicy.decide("angry", 60, 50, "bread", 10, GiftPolicy.Tier.EPIC, false, none, 0.9);
            check(d.allowed() && d.reason().equals("grudging") && d.count() == 4, "enfadada da poco y de mala gana");
            d = GiftPolicy.decide("friendly", 0, 90, "command_block", 1, GiftPolicy.Tier.ADMIN, false, new long[]{0, 0, 0, 0}, 0.9);
            check(d.allowed(), "con muchisimo carino da un bloque de comandos");
            d = GiftPolicy.decide("friendly", 0, 60, "command_block", 1, GiftPolicy.Tier.ADMIN, false, new long[]{0, 0, 0, 0}, 0.9);
            check(!d.allowed(), "sin carino suficiente no da bloques de comandos");
            d = GiftPolicy.decide("friendly", 0, 90, "command_block", 1, GiftPolicy.Tier.EPIC, false, new long[]{0, 0, 0, 0}, 0.9);
            check(!d.allowed(), "la config puede prohibir objetos admin");
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

    /** HttpClient que manda todo a un servidor local (para probar las llamadas sin internet). */
    static final class RewritingClient extends HttpClient {
        private final HttpClient real = HttpClient.newHttpClient();
        private final String base;
        RewritingClient(String base) { this.base = base; }
        private java.net.http.HttpRequest rw(java.net.http.HttpRequest r) {
            java.net.URI u = r.uri();
            java.net.http.HttpRequest.Builder b = java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + u.getRawPath() + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery())));
            r.headers().map().forEach((k, vs) -> vs.forEach(v -> b.header(k, v)));
            b.method(r.method(), r.bodyPublisher().orElse(java.net.http.HttpRequest.BodyPublishers.noBody()));
            return b.build();
        }
        @Override public <T> java.net.http.HttpResponse<T> send(java.net.http.HttpRequest r, java.net.http.HttpResponse.BodyHandler<T> h) throws java.io.IOException, InterruptedException { return real.send(rw(r), h); }
        @Override public <T> java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(java.net.http.HttpRequest r, java.net.http.HttpResponse.BodyHandler<T> h) { return real.sendAsync(rw(r), h); }
        @Override public <T> java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(java.net.http.HttpRequest r, java.net.http.HttpResponse.BodyHandler<T> h, java.net.http.HttpResponse.PushPromiseHandler<T> p) { return real.sendAsync(rw(r), h); }
        @Override public java.util.Optional<java.net.CookieHandler> cookieHandler() { return real.cookieHandler(); }
        @Override public java.util.Optional<java.time.Duration> connectTimeout() { return real.connectTimeout(); }
        @Override public Redirect followRedirects() { return real.followRedirects(); }
        @Override public java.util.Optional<java.net.ProxySelector> proxy() { return real.proxy(); }
        @Override public javax.net.ssl.SSLContext sslContext() { return real.sslContext(); }
        @Override public javax.net.ssl.SSLParameters sslParameters() { return real.sslParameters(); }
        @Override public java.util.Optional<java.net.Authenticator> authenticator() { return real.authenticator(); }
        @Override public Version version() { return real.version(); }
        @Override public java.util.Optional<java.util.concurrent.Executor> executor() { return real.executor(); }
    }
}
