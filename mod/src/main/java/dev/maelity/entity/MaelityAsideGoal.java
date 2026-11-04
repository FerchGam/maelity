package dev.maelity.entity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Se aparta detras y a un lado del jugador para no estorbar cuando pone bloques. */
class MaelityAsideGoal extends Goal {
    private final MaelityEntity maelity;
    private int recalc;

    MaelityAsideGoal(MaelityEntity maelity) {
        this.maelity = maelity;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() { return maelity.isMovingAside() && maelity.getOwner() != null; }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public void start() { recalc = 0; }

    @Override
    public void tick() {
        Player o = maelity.getOwner();
        if (o == null) return;
        maelity.getLookControl().setLookAt(o, 30f, 30f);
        if (--recalc > 0) return;
        recalc = 10;
        Vec3 look = o.getViewVector(1f);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-4) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        Vec3 side = new Vec3(-flat.z, 0, flat.x);
        Vec3 dest = o.position().subtract(flat.scale(2.5)).add(side.scale(1.8)).add(0, 2.2, 0);
        maelity.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.4);
    }
}
