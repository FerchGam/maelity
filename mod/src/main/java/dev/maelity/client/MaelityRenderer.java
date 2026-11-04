package dev.maelity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.maelity.MaelityMod;
import dev.maelity.entity.MaelityEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public class MaelityRenderer extends MobRenderer<MaelityEntity, MaelityModel> {
    private static final ResourceLocation BODY = ResourceLocation.fromNamespaceAndPath(MaelityMod.MODID, "textures/entity/body.png");
    /** Color que se muestra ahora mismo por entidad: se acerca poco a poco al del humor (transicion suave). */
    private final Map<Integer, float[]> shown = new HashMap<>();

    public MaelityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new MaelityModel(ctx.bakeLayer(MaelityModel.LAYER)), 0.3f);
        addLayer(new MaelityFaceLayer(this));
    }

    @Override
    public void render(MaelityEntity e, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        getModel().prepare(MaelityLook.ball(), currentColor(e));
        super.render(e, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private int currentColor(MaelityEntity e) {
        int target = MaelityLook.colorFor(e.getPersonality());
        float tr = (target >> 16 & 255) / 255f, tg = (target >> 8 & 255) / 255f, tb = (target & 255) / 255f;
        if (shown.size() > 64) shown.clear();
        float[] c = shown.computeIfAbsent(e.getId(), k -> new float[]{tr, tg, tb});
        float k = 0.08f;
        c[0] += (tr - c[0]) * k;
        c[1] += (tg - c[1]) * k;
        c[2] += (tb - c[2]) * k;
        return (Math.round(c[0] * 255) << 16) | (Math.round(c[1] * 255) << 8) | Math.round(c[2] * 255);
    }

    /** Cuando ilumina, se ve brillante aunque sea de noche. */
    @Override
    protected int getBlockLightLevel(MaelityEntity e, BlockPos pos) {
        return e.isLit() ? 15 : Math.max(6, super.getBlockLightLevel(e, pos));
    }

    @Override
    public ResourceLocation getTextureLocation(MaelityEntity entity) {
        return BODY;
    }
}
