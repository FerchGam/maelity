package dev.maelity.core;

/** Direccion cardinal legible. En Minecraft: +X = este, +Z = sur. */
public final class Compass {
    private static final String[] EN = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
    private static final String[] ES = {"norte", "noreste", "este", "sureste", "sur", "suroeste", "oeste", "noroeste"};

    public static String direction(double dx, double dz, String lang) {
        if (Math.abs(dx) < 1 && Math.abs(dz) < 1) return "es".equals(lang) ? "justo aqui" : "right here";
        double ang = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = norte, 90 = este
        if (ang < 0) ang += 360;
        int idx = (int) Math.round(ang / 45.0) % 8;
        return ("es".equals(lang) ? ES : EN)[idx];
    }

    private Compass() {}
}
