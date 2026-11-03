package dev.maelity.entity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;

/** Sigue al dueno; si queda muy lejos (o en otra dimension) se teletransporta a su lado. */
class MaelityFollowGoal extends Goal {
    private final MaelityEntity maelity;
    private final double speed;
    private final float stopDist;
    private final float startDist;
    private Player owner;
    private int recalc;

    MaelityFollowGoal(MaelityEntity maelity, double speed, float stopDist, float startDist) {
        this.maelity = maelity;
        this.speed = speed;
        this.stopDist = stopDist;
        this.startDist = startDist;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (maelity.isStay()) return false;
        Player o = maelity.getOwner();
        if (o == null || o.isSpectator()) return false;
        if (maelity.distanceToSqr(o) < (double) (startDist * startDist)) return false;
        owner = o;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !maelity.isStay() && owner != null && owner.isAlive()
                && maelity.distanceToSqr(owner) > (double) (stopDist * stopDist);
    }

    @Override
    public void start() { recalc = 0; }

    @Override
    public void stop() {
        owner = null;
        maelity.getNavigation().stop();
    }

    @Override
    public void tick() {
        maelity.getLookControl().setLookAt(owner, 30f, 30f);
        if (--recalc > 0) return;
        recalc = 8;
        double d2 = maelity.distanceToSqr(owner);
        if (d2 > 40 * 40 || maelity.level() != owner.level()) {
            maelity.teleportTo(owner.getX() + (maelity.getRandom().nextDouble() - 0.5) * 3,
                    owner.getY() + 1.2, owner.getZ() + (maelity.getRandom().nextDouble() - 0.5) * 3);
            maelity.getNavigation().stop();
        } else {
            maelity.getNavigation().moveTo(owner.getX(), owner.getY() + 1.3, owner.getZ(), speed);
        }
    }
}
