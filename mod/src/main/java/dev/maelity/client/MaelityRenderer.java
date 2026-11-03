package dev.maelity.client;

import dev.maelity.MaelityMod;
import dev.maelity.ai.Personality;
import dev.maelity.entity.MaelityEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class MaelityRenderer extends MobRenderer<MaelityEntity, MaelityModel> {
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[Personality.values().length];

    static {
        for (Personality p : Personality.values())
            TEXTURES[p.ordinal()] = ResourceLocation.fromNamespaceAndPath(MaelityMod.MODID, "textures/entity/maelity_" + p.id + ".png");
    }

    public MaelityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new MaelityModel(ctx.bakeLayer(MaelityModel.LAYER)), 0.3f);
    }

    @Override
    public ResourceLocation getTextureLocation(MaelityEntity entity) {
        return TEXTURES[entity.getPersonality().ordinal()];
    }
}
