package dev.maelity.net;

import dev.maelity.MaelityConfig;
import dev.maelity.client.VoiceClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Solo se invoca en el cliente (nunca se referencia desde codigo de servidor). */
public final class ClientHandlers {

    public static void onSay(SayPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.getChat().addMessage(Component.literal("§e<Maelity>§r " + p.text()));
            VoiceClient.thinkingDone();
            if (MaelityConfig.CLIENT.voiceEnabled.get()) VoiceClient.speak(p.text(), p.lang(), p.mood());
        });
    }

    private ClientHandlers() {}
}
