package dev.maelity.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Bloqueo por edad: la primera edad que se pone queda guardada en un archivo aparte (fuera de la configuracion).
 * Si es menor de 18, las groserias quedan bloqueadas aunque luego se cambie la configuracion. Para reiniciarlo hay que
 * borrar los datos del mod (la carpeta .minecraft/maelity). Java puro.
 */
public final class AgeGate {
    /** @return la edad bloqueada (0 = aun sin poner). Si configAge > 0 y no habia bloqueo, la guarda. */
    public static int lockedAge(Path lockFile, int configAge) {
        try {
            if (Files.exists(lockFile)) {
                int a = Integer.parseInt(Files.readString(lockFile, StandardCharsets.UTF_8).trim());
                return Math.max(0, a);
            }
            if (configAge > 0) {
                Files.createDirectories(lockFile.getParent());
                Files.writeString(lockFile, Integer.toString(configAge), StandardCharsets.UTF_8);
                return configAge;
            }
        } catch (Exception e) {
            return configAge > 0 && configAge < 18 ? configAge : 0;   // ante la duda, no se permite
        }
        return 0;
    }

    public static boolean profanityAllowed(Path lockFile, int configAge, boolean wantsProfanity) {
        int age = lockedAge(lockFile, configAge);
        return wantsProfanity && age >= 18;
    }

    private AgeGate() {}
}
