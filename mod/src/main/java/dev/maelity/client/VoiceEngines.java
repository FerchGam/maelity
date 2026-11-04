package dev.maelity.client;

import dev.maelity.MaelityConfig;
import dev.maelity.MaelityMod;
import dev.maelity.core.CloudTts;
import dev.maelity.core.PiperTts;
import dev.maelity.core.WindowsTts;
import dev.maelity.net.TrustStores;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Elige el motor de voz configurado (Piper / ElevenLabs / Fish Audio / Windows). Si falla, usa la voz de Windows. */
public final class VoiceEngines {
    private static final HttpClient HTTP = build();
    private static final ExecutorService PIPER_EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Maelity-Piper");
        t.setDaemon(true);
        return t;
    });
    private static volatile PiperTts piper;
    private static volatile long lastWarn;

    private static HttpClient build() {
        HttpClient.Builder b = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1);
        javax.net.ssl.SSLContext ctx = TrustStores.javaPlusWindows();
        if (ctx != null) b.sslContext(ctx);
        return b.build();
    }

    private static PiperTts piper() {
        if (piper == null) piper = new PiperTts(FMLPaths.GAMEDIR.get().resolve("maelity").resolve("piper"), HTTP);
        return piper;
    }

    private static boolean female() { return MaelityConfig.CLIENT.voiceGender.get() != dev.maelity.Options.Gender.MALE; }

    static String piperVoice(String lang) {
        boolean es = "es".equals(lang);
        String custom = es ? MaelityConfig.CLIENT.piperVoiceEs.get().key : MaelityConfig.CLIENT.piperVoiceEn.get().key;
        if (PiperTts.validVoice(custom)) return custom;
        if (es) return female() ? "es_MX-claude-high" : "es_MX-ald-medium";
        return female() ? "en_US-amy-medium" : "en_US-ryan-medium";
    }

    public static CompletableFuture<byte[]> synth(String text, String lang, String mood) {
        String engine = MaelityConfig.CLIENT.voiceEngine.get().id();
        CompletableFuture<byte[]> f = switch (engine) {
            case "piper" -> CompletableFuture.supplyAsync(() -> {
                try {
                    return piper().synth(text, piperVoice(lang), mood, what -> chat(
                            "voice".equals(what)
                                    ? tr(lang, "§7Descargando la voz de Maelity (solo la primera vez, unos segundos)...", "§7Downloading Maelity's voice (first time only)...")
                                    : tr(lang, "§7Instalando el motor de voz Piper (solo la primera vez)...", "§7Installing the Piper voice engine (first time only)...")));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, PIPER_EXEC);
            case "elevenlabs" -> {
                String id = MaelityConfig.CLIENT.elevenVoiceId.get().trim();
                if (id.isEmpty()) id = female() ? CloudTts.ELEVEN_FEMALE : CloudTts.ELEVEN_MALE;
                yield CloudTts.elevenLabs(HTTP, MaelityConfig.CLIENT.elevenApiKey.get(), id, text);
            }
            case "fish" -> CloudTts.fishAudio(HTTP, MaelityConfig.CLIENT.fishApiKey.get(),
                    MaelityConfig.CLIENT.fishModel.get().id, MaelityConfig.CLIENT.fishVoiceId.get(), text);
            default -> WindowsTts.synth(text, lang, mood);
        };
        if ("windows".equals(engine)) return f;
        return f.exceptionallyCompose(err -> {
            MaelityMod.LOGGER.warn("Maelity voz '{}' fallo, uso la de Windows: {}", engine, err.toString());
            warn(lang, engine, err);
            return WindowsTts.synth(text, lang, mood);
        });
    }

    private static void warn(String lang, String engine, Throwable err) {
        long now = System.currentTimeMillis();
        if (now - lastWarn < 60_000) return;
        lastWarn = now;
        String why = String.valueOf(err.getMessage());
        boolean key = why.contains("key missing") || why.contains("HTTP 401") || why.contains("HTTP 403");
        boolean quota = why.contains("HTTP 402") || why.contains("HTTP 429") || why.contains("quota");
        String name = switch (engine) { case "elevenlabs" -> "ElevenLabs"; case "fish" -> "Fish Audio"; default -> "Piper"; };
        String msg;
        if (key) msg = tr(lang, "§eFalta o no sirve la clave de " + name + " (Mods > Maelity > Config > Voz). Uso la voz de Windows.",
                "§eThe " + name + " key is missing or invalid (Mods > Maelity > Config > Voice). Using the Windows voice.");
        else if (quota) msg = tr(lang, "§eSe acabaron los créditos gratis de " + name + " por ahora. Uso la voz de Windows.",
                "§eYour free " + name + " credits ran out for now. Using the Windows voice.");
        else msg = tr(lang, "§eLa voz " + name + " no funcionó. Uso la voz de Windows.", "§eThe " + name + " voice failed. Using the Windows voice.");
        chat(msg);
    }

    private static String tr(String lang, String es, String en) { return "es".equals(lang) ? es : en; }

    private static void chat(String msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> { if (mc.player != null) mc.gui.getChat().addMessage(Component.literal(msg)); });
    }

    private VoiceEngines() {}
}
