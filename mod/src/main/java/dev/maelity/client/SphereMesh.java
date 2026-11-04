package dev.maelity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;

/**
 * Esfera lisa (no de bloques) dibujada a mano. Coordenadas en "pixeles" de modelo (1/16 de bloque), centro en el
 * pivote de la pieza, Y hacia abajo y la cara mirando a -Z, igual que las cajas del modelo.
 */
final class SphereMesh {
    private static final int LAT = 18;   // anillos
    private static final int LON = 28;   // gajos

    /**
     * @param faceOnly true = solo el frente, con la textura de la cara proyectada (la cara 10x10 de la textura 64x32)
     */
    static void render(PoseStack.Pose pose, VertexConsumer vc, float radius, int light, int overlay, int color, boolean faceOnly) {
        float faceScale = 1.2f;                       // la cara se ve un poco mas pequena para caber en la bola
        for (int i = 0; i < LAT; i++) {
            float p0 = (float) Math.PI * i / LAT;
            float p1 = (float) Math.PI * (i + 1) / LAT;
            for (int j = 0; j < LON; j++) {
                float t0 = (float) (2 * Math.PI) * j / LON;
                float t1 = (float) (2 * Math.PI) * (j + 1) / LON;
                float[][] q = {pt(p0, t0), pt(p1, t0), pt(p1, t1), pt(p0, t1)};   // x, y, z unitarios
                if (faceOnly) {
                    boolean front = true;
                    for (float[] v : q) if (v[2] > -0.15f) { front = false; break; }
                    if (!front) continue;
                }
                for (float[] v : q) {
                    float x = v[0] * radius, y = v[1] * radius, z = v[2] * radius;
                    float u, w;
                    if (faceOnly) {
                        u = (x * faceScale + 5f) / 64f;       // mismo sentido que la cara norte de una caja: -X a la izquierda
                        w = (y * faceScale + 5f) / 32f;       // -Y (arriba) arriba
                    } else {
                        u = (float) j / LON * (40f / 64f);
                        w = (float) i / LAT * (20f / 32f);
                    }
                    Vector3f pos = pose.pose().transformPosition(x / 16f, y / 16f, z / 16f, new Vector3f());
                    Vector3f n = pose.normal().transform(new Vector3f(v[0], v[1], v[2])).normalize();
                    vc.addVertex(pos.x(), pos.y(), pos.z(), color, u, w, overlay, light, n.x(), n.y(), n.z());
                }
            }
        }
    }

    /** phi 0 = arriba (Y negativo en el modelo), theta alrededor; z = -1 es el frente. */
    private static float[] pt(float phi, float theta) {
        float s = (float) Math.sin(phi);
        return new float[]{s * (float) Math.sin(theta), -(float) Math.cos(phi), -s * (float) Math.cos(theta)};
    }

    private SphereMesh() {}
}
