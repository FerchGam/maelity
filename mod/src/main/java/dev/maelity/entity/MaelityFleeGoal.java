package dev.maelity.entity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Se aleja volando del peligro (creeper a punto de explotar, demasiados monstruos, o ella muy herida). */
class MaelityFleeGoal extends Goal {
    private final MaelityEntity maelity;
    private int recalc;

    MaelityFleeGoal(MaelityEntity maelity) {
        this.maelity = maelity;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() { return maelity.isFleeing(); }

    @Override
    public boolean canContinueToUse() { return maelity.isFleeing(); }

    @Override
    public void start() { recalc = 0; }

    @Override
    public void stop() { maelity.getNavigation().stop(); }

    @Override
    public void tick() {
        if (--recalc > 0) return;
        recalc = 10;
        Vec3 away = maelity.position().subtract(maelity.fleeFrom());
        away = new Vec3(away.x, 0, away.z);
        if (away.lengthSqr() < 0.01) {
            double a = maelity.getRandom().nextDouble() * Math.PI * 2;
            away = new Vec3(Math.cos(a), 0, Math.sin(a));
        }
        Vec3 dest = maelity.position().add(away.normalize().scale(8)).add(0, 3, 0);
        maelity.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.8);
    }
}
