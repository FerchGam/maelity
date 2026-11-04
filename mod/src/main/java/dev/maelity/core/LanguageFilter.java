package dev.maelity.core;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Red de seguridad: si las groserias no estan permitidas, se tapan en lo que dice Maelity (p***). Java puro. */
public final class LanguageFilter {
    private static final List<String> WORDS = List.of(
            // espanol
            "mierda", "puta", "puto", "putas", "putos", "joder", "jodete", "jodido", "jodida", "cono", "carajo", "verga",
            "pendejo", "pendeja", "pendejos", "cabron", "cabrona", "cabrones", "chingar", "chinga", "chingada", "chingado",
            "malparido", "malparida", "gilipollas", "hijueputa", "hdp", "culero", "culera", "pinche", "huevon", "huevona",
            "maricon", "concha", "boludo", "pelotudo", "mamon", "mamada",
            // ingles
            "fuck", "fucking", "fucker", "motherfucker", "shit", "shitty", "bitch", "bitches", "asshole", "bastard",
            "dick", "cunt", "damn", "goddamn", "piss", "crap", "bullshit", "wtf");
    private static final Pattern P;

    static {
        StringBuilder sb = new StringBuilder("(?iu)(?<![\\p{L}])(");
        for (int i = 0; i < WORDS.size(); i++) sb.append(i == 0 ? "" : "|").append(Pattern.quote(WORDS.get(i)));
        P = Pattern.compile(sb.append(")(?![\\p{L}])").toString());
    }

    private static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /** "eres un pendejo" -> "eres un p******". Respeta acentos (busca sin acentos, tapa el original). */
    public static String censor(String text) {
        if (text == null || text.isEmpty()) return text;
        String folded = fold(text);
        if (folded.length() != text.length()) return censorSimple(text);   // raro: caracteres compuestos
        Matcher m = P.matcher(folded);
        StringBuilder out = new StringBuilder(text);
        while (m.find()) {
            for (int i = m.start() + 1; i < m.end(); i++) out.setCharAt(i, '*');
        }
        return out.toString();
    }

    private static String censorSimple(String text) {
        Matcher m = P.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String w = m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(w.charAt(0) + "*".repeat(w.length() - 1)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static boolean hasProfanity(String text) { return text != null && P.matcher(fold(text)).find(); }

    private LanguageFilter() {}
}
