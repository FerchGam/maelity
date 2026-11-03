package dev.maelity.ai;

/** Las personalidades de Maelity. El id viaja al modelo y al backend de voz (estilo de la voz). */
public enum Personality {
    FRIENDLY("friendly",
            "Warm, kind, encouraging. Cheerful helper who genuinely cares about the player."),
    PLAYFUL("playful",
            "Mischievous, joking, energetic. Teases lightly, loves banter and silly comments."),
    SARCASTIC("sarcastic",
            "Dry, deadpan, witty. Gentle sarcasm and mock-exasperation, but still helps."),
    PROTECTIVE("protective",
            "Alert, serious, caring. Short urgent sentences; prioritizes the player's safety."),
    MYSTERIOUS("mysterious",
            "Calm, cryptic, unsettling. Speaks slowly with hints and unsettling observations, never cruel."),
    ANGRY("angry",
            "Irritated and fed up with the player. Curt, sharp, scolding. Still never uses slurs or real insults.");

    public final String id;
    public final String guide;

    Personality(String id, String guide) {
        this.id = id;
        this.guide = guide;
    }

    public static Personality fromId(String id) {
        if (id != null) for (Personality p : values()) if (p.id.equalsIgnoreCase(id.trim())) return p;
        return null;
    }

    public static Personality fromOrdinal(int i) {
        Personality[] v = values();
        return i >= 0 && i < v.length ? v[i] : FRIENDLY;
    }

    public static String allIds() {
        StringBuilder sb = new StringBuilder();
        for (Personality p : values()) sb.append(sb.length() == 0 ? "" : "|").append(p.id);
        return sb.toString();
    }
}
