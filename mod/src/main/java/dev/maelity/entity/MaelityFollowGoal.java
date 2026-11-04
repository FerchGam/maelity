package dev.maelity.entity;

import dev.maelity.MaelityConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Sigue al dueno con calma: si estas quieto, ella se queda quieta; solo se mueve cuando te alejas.
 * Estilos (Config > Movimiento): "calm" se acerca por el lado donde ya esta (no cruza por delante),
 * "behind" se coloca detras de tu cuerpo (no de tu mirada, asi no se mueve al girar la cabeza).
 */
class MaelityFollowGoal extends Goal {
    private final MaelityEntity maelity;
    private Player owner;
    private int recalc;
    private Vec3 lastTarget;

    MaelityFollowGoal(MaelityEntity maelity) {
        this.maelity = maelity;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private static double stopDist() { return MaelityConfig.COMMON.followDistance.get(); }
    private static double startDist() { return stopDist() + 2.5; }

    @Override
    public boolean canUse() {
        if (maelity.isStay() || maelity.isMovingAside()) return false;
        Player o = maelity.getOwner();
        if (o == null || o.isSpectator()) return false;
        double s = startDist();
        if (maelity.distanceToSqr(o) < s * s) return false;
        owner = o;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        double s = stopDist();
        return !maelity.isStay() && !maelity.isMovingAside() && owner != null && owner.isAlive()
                && maelity.distanceToSqr(owner) > s * s;
    }

    @Override
    public void start() { recalc = 0; lastTarget = null; }

    @Override
    public void stop() {
        owner = null;
        lastTarget = null;
        maelity.getNavigation().stop();
    }

    @Override
    public void tick() {
        maelity.getLookControl().setLookAt(owner, 30f, 30f);
        if (--recalc > 0) return;
        recalc = 10;
        double d2 = maelity.distanceToSqr(owner);
        if (d2 > 40 * 40 || maelity.level() != owner.level()) {
            maelity.teleportTo(owner.getX() + (maelity.getRandom().nextDouble() - 0.5) * 3,
                    owner.getY() + 1.2, owner.getZ() + (maelity.getRandom().nextDouble() - 0.5) * 3);
            maelity.getNavigation().stop();
            lastTarget = null;
            return;
        }
        double dist = stopDist() * 0.8;
        Vec3 target;
        if (MaelityConfig.COMMON.followStyle.get() == dev.maelity.Options.FollowStyle.BEHIND) {
            float yaw = owner.yBodyRot * Mth.DEG_TO_RAD;            // el cuerpo, no la cabeza
            Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
            target = owner.position().subtract(fwd.scale(dist)).add(side.scale(1.2)).add(0, 1.6, 0);
        } else {
            // "calm": se acerca en linea recta desde donde esta y se detiene a tu lado, sin cruzarse delante
            Vec3 away = new Vec3(maelity.getX() - owner.getX(), 0, maelity.getZ() - owner.getZ());
            if (away.lengthSqr() < 1e-4) away = new Vec3(1, 0, 0);
            target = owner.position().add(away.normalize().scale(dist)).add(0, 1.6, 0);
        }
        // no recalcular por pequenos cambios: evita que tiemble
        if (lastTarget != null && lastTarget.distanceToSqr(target) < 1.5 * 1.5 && !maelity.getNavigation().isDone()) return;
        lastTarget = target;
        maelity.getNavigation().moveTo(target.x, target.y, target.z, MaelityConfig.COMMON.followSpeed.get());
    }
}
