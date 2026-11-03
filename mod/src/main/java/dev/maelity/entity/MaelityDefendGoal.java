package dev.maelity.entity;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Vuela hacia el monstruo que ataca al jugador y lo golpea. */
class MaelityDefendGoal extends Goal {
    private final MaelityEntity maelity;
    private LivingEntity target;
    private int cooldown;
    private int recalc;

    MaelityDefendGoal(MaelityEntity maelity) {
        this.maelity = maelity;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity t = maelity.getCombatTarget();
        if (t == null || !t.isAlive() || t.level() != maelity.level() || maelity.isFleeing()) return false;
        target = t;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && target.isAlive() && !maelity.isFleeing()
                && maelity.getCombatTarget() == target && maelity.distanceToSqr(target) < 40 * 40;
    }

    @Override
    public void start() { cooldown = 0; recalc = 0; }

    @Override
    public void stop() {
        if (target != null && !target.isAlive()) maelity.onDefeated(target);
        target = null;
        maelity.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (cooldown > 0) cooldown--;
        maelity.getLookControl().setLookAt(target, 40f, 40f);
        double reach = 1.6 + target.getBbWidth();
        if (maelity.distanceToSqr(target) > reach * reach) {
            if (--recalc <= 0) {
                recalc = 5;
                maelity.getNavigation().moveTo(target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 1.6);
            }
        } else if (cooldown <= 0) {
            maelity.swing(InteractionHand.MAIN_HAND);
            maelity.doHurtTarget(target);
            cooldown = 20;
        }
    }
}
