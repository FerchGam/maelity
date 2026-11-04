package dev.maelity.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Cuando hay un destino (resultado de una busqueda con "guide"), Maelity vuela unos bloques por delante
 * del jugador en direccion al objetivo. Si el jugador se aleja mucho, espera.
 */
class MaelityGuideGoal extends Goal {
    private final MaelityEntity maelity;
    private int recalc;

    MaelityGuideGoal(MaelityEntity maelity) {
        this.maelity = maelity;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Player o = maelity.getOwner();
        return maelity.getGuideTarget() != null && o != null && o.level() == maelity.level();
    }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public void tick() {
        Player o = maelity.getOwner();
        BlockPos t = maelity.getGuideTarget();
        if (o == null || t == null) return;

        double dx = t.getX() + 0.5 - o.getX();
        double dz = t.getZ() + 0.5 - o.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 8) { // llegamos
            maelity.onArrived();
            return;
        }
        if (--recalc > 0) return;
        recalc = 10;

        if (maelity.distanceToSqr(o) > 24 * 24) {
            double mx = t.getX() + 0.5 - maelity.getX(), mz = t.getZ() + 0.5 - maelity.getZ();
            boolean playerBehind = mx * mx + mz * mz < horiz * horiz;   // Maelity va mas cerca del destino que el jugador
            if (playerBehind) {                                        // el jugador se quedo atras: esperarlo
                maelity.getNavigation().stop();
                maelity.getLookControl().setLookAt(o, 30f, 30f);
            } else if (maelity.distanceToSqr(o) > 40 * 40) {           // ella se quedo atras (peleando...): alcanzarlo
                maelity.teleportTo(o.getX(), o.getY() + 1.6, o.getZ());
                maelity.getNavigation().stop();
            } else {
                maelity.getNavigation().moveTo(o.getX(), o.getY() + 1.6, o.getZ(), 1.5);
            }
            return;
        }
        double step = Math.min(7.0, horiz);
        Vec3 p = new Vec3(o.getX() + dx / horiz * step, o.getY() + 1.6, o.getZ() + dz / horiz * step);
        maelity.getNavigation().moveTo(p.x, p.y, p.z, 1.1);
        maelity.getLookControl().setLookAt(p.x, p.y, p.z);
    }
}
