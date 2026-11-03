package dev.maelity.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.maelity.MaelityMod;
import dev.maelity.ModEntities;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Registro (bus del mod) de la tecla, el renderer y el modelo. */
@EventBusSubscriber(modid = MaelityMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    public static final KeyMapping TALK_KEY =
            new KeyMapping("key.maelity.talk", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.maelity");

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent e) {
        e.register(TALK_KEY);
    }

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(MaelityModel.LAYER, MaelityModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.MAELITY.get(), MaelityRenderer::new);
    }

    private ClientModEvents() {}
}
