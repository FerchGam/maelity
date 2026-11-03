package dev.maelity;

import com.mojang.logging.LogUtils;
import dev.maelity.entity.MaelityEntity;
import dev.maelity.net.ClientHandlers;
import dev.maelity.net.SayPayload;
import dev.maelity.net.ServerHandlers;
import dev.maelity.net.SpeakPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@Mod(MaelityMod.MODID)
public class MaelityMod {
    public static final String MODID = "maelity";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MaelityMod(IEventBus modBus, ModContainer container) {
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        modBus.addListener(MaelityMod::onAttributes);
        modBus.addListener(MaelityMod::onPayloads);
        modBus.addListener(ModItems::onCreativeTab);
        container.registerConfig(ModConfig.Type.COMMON, MaelityConfig.COMMON_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, MaelityConfig.CLIENT_SPEC);
    }

    private static void onAttributes(EntityAttributeCreationEvent e) {
        e.put(ModEntities.MAELITY.get(), MaelityEntity.createAttributes().build());
    }

    private static void onPayloads(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar("1");
        r.playToServer(SpeakPayload.TYPE, SpeakPayload.CODEC, ServerHandlers::onSpeak);
        r.playToClient(SayPayload.TYPE, SayPayload.CODEC, ClientHandlers::onSay);
    }
}
