package dev.maelity.ai;

import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Random;

/**
 * Hace que la personalidad fluya con la situacion y la conversacion:
 *  - el MODELO propone cambios (suggest) con inercia: no mas de uno cada 15 s;
 *  - el JUEGO fuerza cambios (vida baja -> protectora, golpes al jugador -> enfadada, noche -> misteriosa...).
 */
public final class MoodEngine {
    private static final int SUGGEST_COOLDOWN = 20 * 15;
    private static final Random RNG = new Random();

    private Personality current = Personality.FRIENDLY;
    private float anger = 0f;          // 0..100
    private long lockedUntil = 0;      // ticks de juego: no se cambia salvo fuerza mayor
    private long lastChange = 0;
    private long lastTalk = 0;

    public Personality get() { return current; }
    public float anger() { return anger; }

    public void load(Personality p, float anger) {
        this.current = p == null ? Personality.FRIENDLY : p;
        this.anger = anger;
    }

    public void onPlayerTalk(long now) { lastTalk = now; }

    /** El jugador le pego a Maelity. */
    public void onHurtByOwner(long now) {
        anger = Math.min(100f, anger + 35f);
        if (anger >= 70f) force(Personality.ANGRY, now, 20 * 30);
    }

    /** Ajuste directo del enfado (positivo o negativo), siempre dentro de 0..100. */
    public void addAnger(float amount) { anger = Math.max(0f, Math.min(100f, anger + amount)); }

    /** El jugador la insulto: cada insulto suma; si se acumula mucho se enfada de verdad (sin esperar al modelo). */
    public void onInsulted(long now) {
        addAnger(25f);
        if (anger >= 70f) force(Personality.ANGRY, now, 20 * 30);
    }

    /** El jugador fue amable: se le baja el enfado. */
    public void onKindness() { addAnger(-15f); }

    /** Cambio propuesto por el modelo. Devuelve true si se aplico. */
    public boolean suggest(Personality wanted, long now) {
        if (wanted == null || wanted == current) return false;
        if (now < lockedUntil) return false;
        if (now - lastChange < SUGGEST_COOLDOWN) return false;
        if (wanted == Personality.ANGRY && anger < 40f) return false;   // no se enfada "porque si"
        set(wanted, now);
        if (wanted == Personality.ANGRY) lockedUntil = now + 20 * 20;
        return true;
    }

    /** Llamar una vez por segundo (cada 20 ticks) en el servidor. */
    public boolean tick(Level level, Player owner, long now) {
        Personality before = current;
        anger = Math.max(0f, anger - 1.5f);

        boolean night = isNight(level);
        if (owner != null) {
            float hp = owner.getHealth();
            boolean danger = hp <= 6f
                    || (hp <= 10f && !level.getEntitiesOfClass(Monster.class, owner.getBoundingBox().inflate(10)).isEmpty());
            if (danger && current != Personality.ANGRY) {
                force(Personality.PROTECTIVE, now, 20 * 20);
            } else if (current == Personality.PROTECTIVE && hp > 14f && now >= lockedUntil) {
                set(Personality.FRIENDLY, now);
            }
        }
        if (current == Personality.ANGRY && anger < 25f && now >= lockedUntil) set(Personality.FRIENDLY, now);

        if (now >= lockedUntil && now - lastChange > SUGGEST_COOLDOWN) {
            if (night && (current == Personality.FRIENDLY || current == Personality.PLAYFUL) && RNG.nextFloat() < 0.04f) {
                set(Personality.MYSTERIOUS, now);
            } else if (!night && current == Personality.MYSTERIOUS && RNG.nextFloat() < 0.15f) {
                set(Personality.FRIENDLY, now);
            } else if (current == Personality.FRIENDLY && now - lastTalk > 20 * 180 && RNG.nextFloat() < 0.05f) {
                set(Personality.PLAYFUL, now);   // se aburre
            } else if (current == Personality.PLAYFUL && now - lastTalk < 20 * 10 && RNG.nextFloat() < 0.01f) {
                set(Personality.SARCASTIC, now);
            }
        }
        return before != current;
    }

    public void force(Personality p, long now, int lockTicks) {
        if (p != current) set(p, now);
        lockedUntil = Math.max(lockedUntil, now + lockTicks);
    }

    private void set(Personality p, long now) {
        current = p;
        lastChange = now;
    }

    private static boolean isNight(Level level) {
        long t = level.getDayTime() % 24000L;
        return t >= 13000L && t <= 23000L;
    }
}
