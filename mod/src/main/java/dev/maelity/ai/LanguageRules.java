package dev.maelity.ai;

import dev.maelity.MaelityConfig;
import dev.maelity.core.AgeGate;
import dev.maelity.core.LanguageFilter;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/** Que tan fuerte puede hablar Maelity: por defecto sin groserias; solo mayores de 18 pueden activarlas. */
public final class LanguageRules {
    private static Path lockFile() { return FMLPaths.GAMEDIR.get().resolve("maelity").resolve("age.lock"); }

    public static boolean profanityAllowed() {
        try {
            return AgeGate.profanityAllowed(lockFile(), MaelityConfig.COMMON.playerAge.get(), MaelityConfig.COMMON.allowProfanity.get());
        } catch (Exception e) {
            return false;
        }
    }

    public static String forPrompt() {
        if (profanityAllowed()) {
            return "LANGUAGE: the player is an adult and enabled strong language. When you are ANGRY (anger >= 70) you may swear "
                    + "and use crude insults in the player's language. Never use slurs, hate speech, sexual content or real threats.\n\n";
        }
        return "LANGUAGE: NO profanity or swear words ever (the player may be a child). When angry you may use mild, family-friendly "
                + "insults only (e.g. 'inútil', 'torpe', 'tonto', 'useless', 'clumsy', 'silly'). Never slurs.\n\n";
    }

    /** Tapa groserias si no estan permitidas (por si el modelo se pasa). */
    public static String clean(String say) {
        return profanityAllowed() ? say : LanguageFilter.censor(say);
    }

    private LanguageRules() {}
}
