package dev.maelity.core;

/**
 * Que hace Maelity cuando atacan a su jugador: defenderlo, huir, solo avisar o ignorarlo.
 * Depende del humor, de lo herida que este ella, del jugador y del tipo de amenaza. Java puro.
 */
public final class CombatPolicy {
    public enum Choice { DEFEND, FLEE, WARN, IGNORE }

    /**
     * @param maelityHp    vida de Maelity 0..1
     * @param ownerHp      vida del jugador 0..1
     * @param attackers    cuantos enemigos van a por el jugador
     * @param creeperPrimed un creeper esta a punto de explotar cerca
     * @param bossNear     warden / wither / dragon / guardian anciano cerca
     */
    public static Choice decide(String mood, float anger, float maelityHp, float ownerHp, int attackers,
                                boolean creeperPrimed, boolean bossNear, boolean defendEnabled) {
        if (attackers <= 0 && !creeperPrimed && !bossNear) return Choice.IGNORE;
        if (bossNear || creeperPrimed) return Choice.FLEE;
        if (maelityHp < 0.3f) return Choice.FLEE;
        if (!defendEnabled) return Choice.WARN;
        String m = mood == null ? "friendly" : mood;
        switch (m) {
            case "angry":
                // enfadada te deja solo... salvo que estes de verdad en peligro
                return (anger >= 70f && ownerHp > 0.5f) ? Choice.IGNORE : Choice.DEFEND;
            case "mysterious":
                return attackers >= 3 ? Choice.FLEE : Choice.DEFEND;
            case "protective":
                return attackers >= 9 ? Choice.FLEE : Choice.DEFEND;
            default:
                if (attackers >= 6) return ownerHp < 0.4f ? Choice.DEFEND : Choice.FLEE;
                return Choice.DEFEND;
        }
    }

    private CombatPolicy() {}
}
