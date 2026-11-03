package dev.maelity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.maelity.MaelityMod;
import dev.maelity.entity.MaelityEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Cubo amarillo flotante de 10x10x10 con la cara pintada en la textura (una textura por personalidad). */
public class MaelityModel extends EntityModel<MaelityEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MaelityMod.MODID, "maelity"), "main");

    private final ModelPart body;

    public MaelityModel(ModelPart root) {
        this.body = root.getChild("body");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5f, -5f, -5f, 10f, 10f, 10f),
                PartPose.offset(0f, 19f, 0f));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(MaelityEntity e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        body.yRot = netHeadYaw * ((float) Math.PI / 180f);
        body.xRot = headPitch * ((float) Math.PI / 180f);
        body.y = 19f + Mth.sin(ageInTicks * 0.12f) * 1.2f;   // flota
        body.zRot = Mth.sin(ageInTicks * 0.07f) * 0.05f;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        body.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
