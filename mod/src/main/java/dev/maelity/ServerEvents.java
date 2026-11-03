package dev.maelity;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.maelity.ai.Personality;
import dev.maelity.entity.MaelityEntity;
import dev.maelity.find.ResourceFinder;
import dev.maelity.net.ServerHandlers;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Arrays;

@EventBusSubscriber(modid = MaelityMod.MODID)
public final class ServerEvents {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        ResourceFinder.tick(e.getServer());
    }

    /** Escribir "maelity, ..." en el chat tambien le habla (alternativa al microfono). */
    @SubscribeEvent
    public static void onChat(ServerChatEvent e) {
        String raw = e.getRawText().trim();
        if (raw.length() > 5 && raw.regionMatches(true, 0, "maelity", 0, 5)) {
            String text = raw.substring(5).replaceFirst("^[\\s,:;!¡]+", "");
            ServerPlayer sp = e.getPlayer();
            if (!text.isEmpty()) {
                sp.getServer().execute(() -> ServerHandlers.ask(sp, text, MaelityEntity.clientLang(sp)));
            }
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("maelity")
                .then(Commands.literal("summon").executes(ServerEvents::summon))
                .then(Commands.literal("ask")
                        .then(Commands.argument("text", StringArgumentType.greedyString()).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            ServerHandlers.ask(p, StringArgumentType.getString(ctx, "text"), MaelityEntity.clientLang(p));
                            return 1;
                        })))
                .then(Commands.literal("key")
                        .requires(s -> s.getServer().isSingleplayer() || s.hasPermission(2))
                        .then(Commands.argument("clave", StringArgumentType.greedyString()).executes(ctx -> {
                            String k = StringArgumentType.getString(ctx, "clave").trim();
                            MaelityConfig.COMMON.groqApiKey.set(k);
                            MaelityConfig.COMMON.groqApiKey.save();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "§aClave guardada / Key saved. Ya puedes hablar con Maelity / You can talk to Maelity now."), false);
                            return 1;
                        })))
                .then(Commands.literal("mood").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Personality.values()).map(x -> x.id), b))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    Personality m = Personality.fromId(StringArgumentType.getString(ctx, "name"));
                                    MaelityEntity a = MaelityEntity.findFor(p);
                                    if (m == null || a == null) {
                                        ctx.getSource().sendFailure(Component.literal("Unknown mood or no Maelity nearby"));
                                        return 0;
                                    }
                                    a.forceMood(m);
                                    return 1;
                                }))));
    }

    private static int summon(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (MaelityEntity.findFor(p) != null) {
            ctx.getSource().sendFailure(Component.literal("You already have a Maelity nearby."));
            return 0;
        }
        ServerLevel level = p.serverLevel();
        MaelityEntity a = ModEntities.MAELITY.get().create(level);
        if (a == null) return 0;
        a.moveTo(p.getX(), p.getY() + 1.5, p.getZ(), p.getYRot(), 0f);
        a.setOwner(p.getUUID());
        level.addFreshEntity(a);
        a.greet(p);
        return 1;
    }

    private ServerEvents() {}
}
