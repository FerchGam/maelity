package dev.maelity.core;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detector de intenciones local (es/en). Funciona SIN internet: asi Maelity puede buscar recursos
 * y obedecer ordenes basicas aunque el backend de IA no responda. El LLM solo pone la voz.
 */
public final class IntentParser {

    public record Intent(TargetCatalog.Entry find, boolean guide, boolean follow, boolean stay, boolean hitMe) {
        public boolean isEmpty() { return find == null && !follow && !stay && !hitMe; }
    }

    private static final Pattern FIND_VERBS = Pattern.compile(
            "(?<![a-z])(donde|encuentra|busca|buscar|localiza|necesito|necesitas|ayudame|quiero|hay|cerca|llevame|guiame|"
                    + "dime|muestrame|where|find|locate|need|looking|show|help|nearest|closest|take me|lead me|guide|any)(?![a-z])");
    private static final Pattern GUIDE = Pattern.compile(
            "(llevame|guiame|acompaname hasta|take me|lead me|guide me|show me the way)");
    private static final Pattern FOLLOW = Pattern.compile(
            "(sigueme|ven conmigo|ven aqui|follow me|come with me|come here|come back|vuelve conmigo)");
    private static final Pattern STAY = Pattern.compile(
            "(quedate|espera aqui|no me sigas|para de seguirme|stay here|stay put|wait here|stop following|don t follow)");
    private static final Pattern HIT_ME = Pattern.compile(
            "(pegame|golpeame|dame un golpe|dame una bofetada|hit me|punch me|slap me|smack me)");

    private static final Pattern INSULT = Pattern.compile(
            "(?<![a-z])(estupid[ao]|idiota|tont[ao]|inutil|callate|te odio|imbecil|basura|"
                    + "stupid|idiot|dumb|shut up|useless|hate you)(?![a-z])");
    private static final Pattern KIND = Pattern.compile(
            "(?<![a-z])(gracias|eres genial|buen trabajo|bien hecho|te quiero|eres la mejor|"
                    + "thanks|thank you|good job|well done|love you|you re great|you are great)(?![a-z])");

    private static final Pattern YES = Pattern.compile(
            "(?<![a-z])(si|claro|vale|ok|okay|dale|de acuerdo|vamos|por favor|porfa|llevame|guiame|ensename el camino|"
                    + "yes|yeah|yep|sure|lets go|let s go|take me|lead me|guide me|lead the way|show me the way)(?![a-z])");
    private static final Pattern NO = Pattern.compile("(?<![a-z])(no|nope|nah|todavia no|not now|mejor no)(?![a-z])");
    private static final Pattern GUIDE_ME = Pattern.compile(
            "(llevame|guiame|acompaname|ensename el camino|muestrame el camino|take me|lead me|guide me|show me the way|lead the way)");

    /** Respuesta afirmativa ("si", "dale", "yes"...) y sin un "no" delante. */
    public static boolean isYes(String raw) {
        String t = normalize(raw);
        return YES.matcher(t).find() && !NO.matcher(t).find();
    }

    /** Pide que lo guie ("llevame", "guiame"), aunque no diga a donde. */
    public static boolean wantsGuide(String raw) { return GUIDE_ME.matcher(normalize(raw)).find(); }

    private static final Pattern REMEMBER = Pattern.compile(
            "(?<![a-z])(recuerda que|acuerdate de que|acuerdate que|no olvides que|ten en cuenta que|quiero que recuerdes que|"
                    + "remember that|don t forget that|keep in mind that|i want you to remember that) (.{3,})");

    /** "recuerda que mi casa esta junto al rio" -> "mi casa esta junto al rio"; null si no pide recordar nada. */
    public static String rememberThat(String raw) {
        Matcher m = REMEMBER.matcher(normalize(raw).replace("?", ""));
        return m.find() ? m.group(2).trim() : null;
    }

    private static final Pattern ASIDE = Pattern.compile(
            "(?<![a-z])(muevete|apartate|quitate|hazte a un lado|hazte para atras|sal de ahi|no estorbes|me estorbas|"
                    + "move aside|move away|move over|get out of the way|out of my way|step aside|step back|you re in the way)(?![a-z])");

    /** "muevete", "apartate", "get out of the way"... */
    public static boolean wantsAside(String raw) { return ASIDE.matcher(normalize(raw)).find(); }

    /** El jugador la esta insultando (es/en). Sube su enfado sin depender del modelo. */
    public static boolean isInsult(String raw) { return INSULT.matcher(normalize(raw)).find(); }

    /** El jugador esta siendo amable. */
    public static boolean isKind(String raw) { return KIND.matcher(normalize(raw)).find(); }

    public static String normalize(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT);
        n = n.replaceAll("[^a-z0-9? ]", " ").replaceAll("\\s+", " ").trim();
        return n;
    }

    public static Intent parse(String raw) {
        String t = normalize(raw);
        boolean question = raw != null && (raw.contains("?") || raw.contains("¿"));

        // palabra clave mas larga gana ("jungle temple" > "jungle")
        TargetCatalog.Entry best = null;
        int bestLen = 0;
        for (TargetCatalog.Entry e : TargetCatalog.ALL) {
            for (String kw : e.keywords()) {
                Matcher m = Pattern.compile("(?<![a-z])" + Pattern.quote(kw) + "[a-z]*").matcher(t);
                if (m.find() && kw.length() > bestLen) {
                    best = e;
                    bestLen = kw.length();
                }
            }
        }

        boolean wantsFind = best != null && (question || FIND_VERBS.matcher(t).find());
        boolean guide = wantsFind && GUIDE.matcher(t).find();
        boolean hitMe = HIT_ME.matcher(t).find();
        boolean stay = STAY.matcher(t).find();
        boolean follow = !stay && FOLLOW.matcher(t).find();
        return new Intent(wantsFind ? best : null, guide, follow, stay, hitMe);
    }

    private IntentParser() {}
}
