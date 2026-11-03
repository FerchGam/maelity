package dev.maelity.net;

import dev.maelity.ai.MaelityBrain;
import dev.maelity.entity.MaelityEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerHandlers {

    /** Llega en el hilo principal del servidor. */
    public static void onSpeak(SpeakPayload p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        String lang = "es".equals(p.lang()) ? "es" : "en";
        ask(sp, p.text(), lang);
    }

    public static void ask(ServerPlayer sp, String rawText, String lang) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) return;
        if (text.length() > 300) text = text.substring(0, 300);
        MaelityEntity maelity = MaelityEntity.findFor(sp);
        if (maelity == null) {
            sp.sendSystemMessage(Component.literal("es".equals(lang)
                    ? "§7No hay ninguna Maelity cerca de ti. Usa /maelity summon."
                    : "§7There is no Maelity near you. Use /maelity summon."));
            return;
        }
        MaelityBrain.onSpeech(sp, maelity, text, lang);
    }

    private ServerHandlers() {}
}
