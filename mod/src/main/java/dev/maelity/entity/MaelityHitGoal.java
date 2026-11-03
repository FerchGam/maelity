package dev.maelity.entity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** Se acerca al dueno y le da un golpe (el dano y las salvaguardas viven en MaelityEntity.performHit). */
class MaelityHitGoal extends Goal {
    private final MaelityEntity maelity;
    private Player target;
    private int timeout;

    MaelityHitGoal(MaelityEntity maelity) {
        this.maelity = maelity;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!maelity.hasPendingHit()) return false;
        Player o = maelity.getOwner();
        if (o == null || !o.isAlive() || o.level() != maelity.level()) { maelity.clearPendingHit(); return false; }
        target = o;
        return true;
    }

    @Override
    public boolean canContinueToUse() { return maelity.hasPendingHit() && target != null && target.isAlive() && timeout > 0; }

    @Override
    public void start() { timeout = 20 * 8; }

    @Override
    public void stop() {
        maelity.clearPendingHit();
        target = null;
        maelity.getNavigation().stop();
    }

    @Override
    public void tick() {
        timeout--;
        maelity.getLookControl().setLookAt(target, 40f, 40f);
        if (maelity.distanceToSqr(target) > 2.2 * 2.2) {
            maelity.getNavigation().moveTo(target.getX(), target.getY() + 1.0, target.getZ(), 1.5);
        } else {
            maelity.performHit(target);
        }
    }
}
