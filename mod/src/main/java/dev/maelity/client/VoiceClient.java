package dev.maelity.client;

import com.google.gson.JsonParser;
import dev.maelity.MaelityConfig;
import dev.maelity.MaelityMod;
import dev.maelity.core.VoicePlayer;
import dev.maelity.core.VoiceRecorder;
import dev.maelity.core.WindowsTts;
import dev.maelity.net.DirectAi;
import dev.maelity.net.SpeakPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Empujar-para-hablar: grabar -> backend STT -> servidor. Y reproducir la voz de Maelity (TTS). */
public final class VoiceClient {
    private static final VoiceRecorder RECORDER = new VoiceRecorder();
    private static volatile boolean busy;           // transcribiendo o esperando respuesta
    private static volatile long busySince;

    public static String lang() { return MaelityConfig.CLIENT.language.get(); }
    public static String tr(String es, String en) { return "es".equals(lang()) ? es : en; }

    public static boolean isRecording() { return RECORDER.isRecording(); }
    public static boolean isBusy() { return busy && System.currentTimeMillis() - busySince < 12000; }

    public static void thinkingDone() { busy = false; }

    public static void startTalking(Minecraft mc) {
        if (isBusy() || RECORDER.isRecording()) return;
        VoicePlayer.stop();                                  // si Maelity hablaba, la callamos
        String err = RECORDER.start();
        if (err != null) {
            mc.gui.getChat().addMessage(Component.literal(tr("§cNo encuentro ningún micrófono. Revisa el predeterminado de Windows.",
                    "§cNo microphone found. Check your system default input device.")));
        }
    }

    public static void stopTalking(Minecraft mc) {
        if (!RECORDER.isRecording()) return;
        byte[] wav = RECORDER.stopAsWav();
        if (wav == null) {
            mc.player.displayClientMessage(Component.literal(tr("§7No te escuché, mantén V mientras hablas.", "§7I didn't hear you, hold V while you talk.")), true);
            return;
        }
        busy = true;
        busySince = System.currentTimeMillis();
        String lang = lang();
        DirectAi.stt(wav, lang).whenComplete((body, err) -> mc.execute(() -> {
            if (err != null) {
                busy = false;
                MaelityMod.LOGGER.warn("Maelity STT fallo: {}", err.toString());
                if (DirectAi.isNoKey(err)) {
                    mc.gui.getChat().addMessage(Component.literal(tr("§eFalta tu clave gratis de Groq. Usa /maelity key <clave> (mira el README).",
                            "§eYour free Groq key is missing. Use /maelity key <key> (see the README).")));
                } else if (DirectAi.isBadKey(err)) {
                    mc.gui.getChat().addMessage(Component.literal(tr("§cLa clave de Groq no es válida. Usa /maelity key <clave>.",
                            "§cThe Groq key isn't valid. Use /maelity key <key>.")));
                } else {
                    mc.gui.getChat().addMessage(Component.literal(tr("§cNo pude conectar con el servicio de voz.", "§cCouldn't reach the voice service.")));
                }
                return;
            }
            String text = "";
            try { text = JsonParser.parseString(body).getAsJsonObject().get("text").getAsString().trim(); } catch (Exception ignored) { }
            if (text.isEmpty()) {
                busy = false;
                mc.player.displayClientMessage(Component.literal(tr("§7No te entendí, inténtalo otra vez.", "§7I didn't catch that, try again.")), true);
                return;
            }
            mc.gui.getChat().addMessage(Component.literal("§7[🎙] " + text));
            PacketDistributor.sendToServer(new SpeakPayload(text, lang));
        }));
    }

    public static void cancel() { RECORDER.cancel(); }

    /** Sintetiza la voz con las voces de Windows (gratis, sin internet) y la reproduce. */
    public static void speak(String text, String lang, String mood) {
        WindowsTts.synth(text, lang, mood).whenComplete((wav, err) -> {
            if (err != null) {
                MaelityMod.LOGGER.debug("Maelity TTS no disponible: {}", err.toString());
                return;                                      // el texto ya se mostro en el chat
            }
            VoicePlayer.play(wav, MaelityConfig.CLIENT.volume.get().floatValue());
        });
    }

    private VoiceClient() {}
}
