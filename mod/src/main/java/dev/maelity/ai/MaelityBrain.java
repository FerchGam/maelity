package dev.maelity.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.maelity.MaelityMod;
import dev.maelity.core.IntentParser;
import dev.maelity.core.TargetCatalog;
import dev.maelity.entity.MaelityEntity;
import dev.maelity.find.ResourceFinder;
import dev.maelity.net.DirectAi;
import dev.maelity.net.SayPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Random;

/**
 * Orquesta una conversacion: intencion local (funciona sin internet) + modelo (voz y personalidad).
 * Todo lo que toca el mundo ocurre en el hilo del servidor; las llamadas HTTP son asincronas.
 */
public final class MaelityBrain {
    private static final Gson GSON = new Gson();
    private static final Random RNG = new Random();

    public record Reply(String say, Personality mood, String action, String find, String giveItem, int giveCount) {
        public Reply(String say, Personality mood, String action, String find) { this(say, mood, action, find, null, 0); }
    }

    // ------------------------------------------------------------------ entrada del jugador

    public static void onSpeech(ServerPlayer player, MaelityEntity maelity, String text, String lang) {
        long now = maelity.level().getGameTime();
        maelity.setLang(lang);
        maelity.mood().onPlayerTalk(now);
        if (IntentParser.isInsult(text)) maelity.mood().onInsulted(now);
        else if (IntentParser.isKind(text)) maelity.mood().onKindness();

        IntentParser.Intent intent = IntentParser.parse(text);
        String note = null;
        String fact = IntentParser.rememberThat(text);
        if (fact != null) {
            LongMemory.add(player.getServer(), player.getUUID(), "The player asked me to remember: " + fact);
            note = "The player asked you to remember this, and it is now saved in your long-term memory: '" + fact + "'. Confirm briefly.";
        }
        if (intent.stay()) maelity.setStay(true);
        if (intent.follow()) maelity.setStay(false);
        if (IntentParser.wantsAside(text)) {
            maelity.moveAside();
            note = "The player asked you to move out of the way (they want to place blocks). You are already moving behind them. Acknowledge briefly.";
        }
        if (intent.hitMe()) {
            boolean ok = maelity.requestHit(true);
            note = ok ? "The player asked you to slap them; you are about to do it."
                      : "The player asked you to hit them but you are not allowed right now (too hurt or too soon). Refuse in character.";
        }
        boolean guideNow = false;
        // "si" / "llevame" despues de que Maelity encontro algo: empieza a guiar YA (sin esperar a la IA)
        if (intent.find() == null && maelity.hasGuideOffer()
                && (IntentParser.wantsGuide(text) || (IntentParser.isYes(text) && text.trim().length() <= 40))) {
            maelity.startGuiding();
            guideNow = true;
            note = "The player agreed to be guided to the " + maelity.lastFoundName() + ". You are now flying ahead to lead them; tell them to follow you.";
        }
        if (intent.find() != null && !ResourceFinder.hasJob(player.getUUID())) {
            ResourceFinder.start(player, maelity, intent.find(), intent.guide(), lang);
            note = "You are already searching for '" + intent.find().id() + "' using the world seed; the result will come in a moment.";
        }
        String fb = guideNow ? ("es".equals(lang) ? "¡Sígueme, te llevo!" : "Follow me, I'll take you there!") : fallbackFor(intent, lang);
        ask(player, maelity, text, lang, note, fb);
    }

    /** Evento del juego narrado por Maelity (saludo, resultado de busqueda...). */
    public static void event(ServerPlayer player, MaelityEntity maelity, String eventNote, String fallbackText) {
        ask(player, maelity, "[GAME EVENT] " + eventNote, maelity.lang(), eventNote, fallbackText);
    }

    // ------------------------------------------------------------------ llamada al modelo

    private static void ask(ServerPlayer player, MaelityEntity maelity, String userText, String lang, String note, String fallback) {
        if (maelity.isThinking()) {
            // ya hay una respuesta en camino: no apilar peticiones
            if (fallback != null) deliver(player, maelity, new Reply(fallback, null, "none", null), lang);
            return;
        }
        maelity.setThinking(true);

        JsonArray msgs = new JsonArray();
        msgs.add(msg("system", PromptBuilder.system(maelity, player, lang, note)));
        for (String[] h : maelity.history()) msgs.add(msg(h[0], h[1]));
        msgs.add(msg("user", userText));

        var server = player.getServer();
        DirectAi.chat(GSON.toJson(msgs)).whenComplete((body, err) -> server.execute(() -> {
            maelity.setThinking(false);
            if (maelity.isRemoved() || server.getPlayerList().getPlayer(player.getUUID()) == null) return;
            Reply reply = null;
            if (err == null) {
                try {
                    String content = JsonParser.parseString(body).getAsJsonObject().get("content").getAsString();
                    reply = parse(content);
                    maelity.remember("user", userText);
                    maelity.remember("assistant", content);
                    if (maelity.countTurnForMemory() >= 6) {
                        maelity.resetMemoryTurns();
                        LongMemory.consolidate(player, maelity);
                    }
                } catch (Exception e) {
                    MaelityMod.LOGGER.warn("Maelity: respuesta ilegible del backend: {}", e.toString());
                }
            } else {
                MaelityMod.LOGGER.warn("Maelity: IA no disponible: {}", err.toString());
                boolean es = "es".equals(lang);
                if (DirectAi.isNoKey(err)) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(es
                            ? "§eMaelity necesita tu clave gratis de Groq para conversar. Mira el README o usa /maelity key <clave>."
                            : "§eMaelity needs your free Groq key to chat. See the README or use /maelity key <key>."));
                } else if (DirectAi.isBadKey(err)) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(es
                            ? "§cLa clave de Groq no es válida. Revísala con /maelity key <clave>."
                            : "§cThe Groq key isn't valid. Set it again with /maelity key <key>."));
                }
            }
            if (reply == null) reply = new Reply(fallback != null ? fallback : offlineLine(lang), null, "none", null);
            deliver(player, maelity, reply, lang);
        }));
    }

    private static JsonObject msg(String role, String content) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", content);
        return o;
    }

    // ------------------------------------------------------------------ aplicar la respuesta

    static Reply parse(String content) {
        String c = content.trim();
        if (c.startsWith("```")) c = c.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "").trim();
        try {
            JsonObject o = JsonParser.parseString(c).getAsJsonObject();
            String say = str(o, "say");
            if (say == null || say.isBlank()) throw new IllegalStateException("no say");
            String giveItem = null;
            int giveCount = 0;
            JsonElement g = o.get("give");
            if (g != null && g.isJsonObject()) {
                giveItem = str(g.getAsJsonObject(), "item");
                JsonElement cnt = g.getAsJsonObject().get("count");
                try { giveCount = cnt == null || cnt.isJsonNull() ? 1 : cnt.getAsInt(); } catch (Exception ex) { giveCount = 1; }
            } else if (g != null && g.isJsonPrimitive()) {
                giveItem = g.getAsString();
                giveCount = 1;
            }
            if (giveItem != null && (giveItem.isBlank() || giveItem.equalsIgnoreCase("null") || giveItem.equalsIgnoreCase("none"))) giveItem = null;
            String find = str(o, "find");
            if (find != null && (find.isBlank() || find.equalsIgnoreCase("null"))) find = null;
            return new Reply(clean(say), Personality.fromId(str(o, "mood")), orDefault(str(o, "action"), "none"), find, giveItem, giveCount);
        } catch (Exception e) {
            return new Reply(clean(c), null, "none", null);  // el modelo hablo sin JSON: igual lo usamos
        }
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static String orDefault(String s, String d) { return s == null ? d : s; }

    private static String clean(String s) {
        String t = s.replaceAll("[\\r\\n]+", " ").replaceAll("[*_`#]", "").trim();
        return t.length() > 260 ? t.substring(0, 260) : t;
    }

    private static void deliver(ServerPlayer player, MaelityEntity maelity, Reply r, String lang) {
        long now = maelity.level().getGameTime();
        if (r.mood() != null) maelity.mood().suggest(r.mood(), now);
        switch (r.action()) {
            case "follow" -> maelity.setStay(false);
            case "stay" -> maelity.setStay(true);
            case "hit_player" -> maelity.requestHit(false);
            case "move_aside" -> maelity.moveAside();
            case "guide" -> { if (r.find() == null && maelity.getGuideTarget() == null) maelity.startGuiding(); }
            case "defend" -> maelity.commandDefend();
            case "flee" -> maelity.commandFlee();
            default -> { }
        }
        String refusal = null;
        if (r.giveItem() != null) {
            Gifts.Result g = Gifts.give(player, maelity, r.giveItem(), r.giveCount());
            if (!g.given()) refusal = Gifts.refusalLine(g, lang);
        }
        if (r.find() != null && !ResourceFinder.hasJob(player.getUUID())) {
            TargetCatalog.Entry e = TargetCatalog.byId(r.find());
            if (e != null) ResourceFinder.start(player, maelity, e, "guide".equals(r.action()), lang);
        }
        PacketDistributor.sendToPlayer(player, new SayPayload(maelity.getId(), r.say(), maelity.mood().get().id, lang));
        if (refusal != null) tellPlayer(player, refusal);
    }

    // ------------------------------------------------------------------ textos sin internet

    private static String fallbackFor(IntentParser.Intent i, String lang) {
        boolean es = "es".equals(lang);
        if (i.find() != null) return es ? "Dejame buscar eso en tu mundo..." : "Let me look for that in your world...";
        if (i.stay()) return es ? "Vale, me quedo aqui." : "Okay, I'll stay here.";
        if (i.follow()) return es ? "Voy contigo." : "I'm coming with you.";
        return null;
    }

    private static String offlineLine(String lang) {
        String[] es = {"Se me cortó la conexión, pero sigo aquí contigo.", "No te escuché bien, ¿puedes repetirlo?", "Mi cabeza anda lenta ahora mismo."};
        String[] en = {"I lost my connection, but I'm still here with you.", "I couldn't think straight, can you say that again?", "My head is a bit slow right now."};
        String[] a = "es".equals(lang) ? es : en;
        return a[RNG.nextInt(a.length)];
    }

    public static void tellPlayer(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(text));
    }

    private MaelityBrain() {}
}
