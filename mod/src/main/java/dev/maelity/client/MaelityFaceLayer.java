package dev.maelity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.maelity.entity.MaelityEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** Dibuja la cara (segun estilo elegido y humor actual) encima del cuerpo, sin tenirla. */
public class MaelityFaceLayer extends RenderLayer<MaelityEntity, MaelityModel> {
    public MaelityFaceLayer(RenderLayerParent<MaelityEntity, MaelityModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, MaelityEntity e,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (e.isInvisible()) return;
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(MaelityLook.faceTexture(e.getPersonality())));
        getParentModel().renderFace(poseStack, vc, packedLight, LivingEntityRenderer.getOverlayCoords(e, 0f));
    }
}
