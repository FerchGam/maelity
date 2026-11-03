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
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Dos formas (cubo o pelotita) con textura blanca que se tine con el color del humor,
 * y una "mascara" plana delante con la cara (se dibuja aparte, sin tenir).
 */
public class MaelityModel extends EntityModel<MaelityEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MaelityMod.MODID, "maelity"), "main");

    private final ModelPart cube;
    private final ModelPart ball;
    private final ModelPart face;
    private boolean useBall;
    private int tint = 0xFFFFFFFF;

    public MaelityModel(ModelPart root) {
        this.cube = root.getChild("cube");
        this.ball = root.getChild("ball");
        this.face = root.getChild("face");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("cube",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5f, -5f, -5f, 10f, 10f, 10f),
                PartPose.offset(0f, 19f, 0f));
        // "pelotita" de bloques: tres cajas cruzadas = cubo con esquinas redondeadas
        root.addOrReplaceChild("ball",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-5f, -4f, -4f, 10f, 8f, 8f)
                        .texOffs(0, 0).addBox(-4f, -5f, -4f, 8f, 10f, 8f)
                        .texOffs(0, 0).addBox(-4f, -4f, -5f, 8f, 8f, 10f),
                PartPose.offset(0f, 19f, 0f));
        // cara: plano 10x10 un pelin por delante (la textura de cara solo pinta los 8x8 centrales)
        root.addOrReplaceChild("face",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5f, -5f, -5.05f, 10f, 10f, 0f),
                PartPose.offset(0f, 19f, 0f));
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** Llamar antes de dibujar: forma elegida y color actual (0xRRGGBB). */
    public void prepare(boolean ball, int rgb) {
        this.useBall = ball;
        this.tint = 0xFF000000 | (rgb & 0xFFFFFF);
    }

    @Override
    public void setupAnim(MaelityEntity e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float yRot = netHeadYaw * ((float) Math.PI / 180f);
        float xRot = headPitch * ((float) Math.PI / 180f);
        float y = 19f + Mth.sin(ageInTicks * 0.12f) * 1.2f;   // flota
        float zRot = Mth.sin(ageInTicks * 0.07f) * 0.05f;
        for (ModelPart p : new ModelPart[]{cube, ball, face}) {
            p.yRot = yRot;
            p.xRot = xRot;
            p.y = y;
            p.zRot = zRot;
        }
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        (useBall ? ball : cube).render(poseStack, buffer, packedLight, packedOverlay, tint);
    }

    public void renderFace(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay) {
        face.render(poseStack, buffer, packedLight, packedOverlay, 0xFFFFFFFF);
    }
}
