package dev.maelity.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.maelity.MaelityConfig;
import dev.maelity.MaelityMod;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.Locale;

/** Eventos de juego del cliente: tecla V (mantener para hablar), pantalla de idioma, comando /maelitylang. */
@EventBusSubscriber(modid = MaelityMod.MODID, value = Dist.CLIENT)
public final class ClientGameEvents {
    private static boolean languagePrompted;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            if (VoiceClient.isRecording()) VoiceClient.cancel();
            languagePrompted = false;
            return;
        }
        // Primera vez: elegir idioma
        if (!MaelityConfig.CLIENT.languageChosen.get() && !languagePrompted && mc.screen == null) {
            languagePrompted = true;
            mc.setScreen(new LanguageScreen());
            return;
        }
        boolean down = ClientModEvents.TALK_KEY.isDown() && mc.screen == null;
        if (down && !VoiceClient.isRecording()) {
            VoiceClient.startTalking(mc);
        } else if (!down && VoiceClient.isRecording()) {
            VoiceClient.stopTalking(mc);
        }
        if (VoiceClient.isRecording()) {
            mc.player.displayClientMessage(Component.literal(VoiceClient.tr("§c● Escuchando... (suelta V al terminar)", "§c● Listening... (release V when done)")), true);
        } else if (VoiceClient.isBusy()) {
            mc.player.displayClientMessage(Component.literal(VoiceClient.tr("§e… Maelity piensa", "§e… Maelity is thinking")), true);
        }
    }

    @SubscribeEvent
    public static void onClientCommands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("maelitylang")
                .then(Commands.argument("lang", StringArgumentType.word())
                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.List.of("es", "en"), b))
                        .executes(ctx -> {
                            String l = StringArgumentType.getString(ctx, "lang").toLowerCase(Locale.ROOT);
                            if (!l.equals("es") && !l.equals("en")) return 0;
                            MaelityConfig.CLIENT.language.set(l);
                            MaelityConfig.CLIENT.languageChosen.set(true);
                            MaelityConfig.CLIENT_SPEC.save();
                            Minecraft.getInstance().gui.getChat().addMessage(
                                    Component.literal(l.equals("es") ? "Maelity: idioma → Español" : "Maelity: language → English"));
                            return 1;
                        })));
    }

    private ClientGameEvents() {}
}
