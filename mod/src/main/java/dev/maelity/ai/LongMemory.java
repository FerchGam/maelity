package dev.maelity.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.maelity.MaelityMod;
import dev.maelity.entity.MaelityEntity;
import dev.maelity.net.DirectAi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memoria a largo plazo de Maelity, una por jugador y por mundo (archivo saves/<mundo>/maelity/memory-<uuid>.json).
 * Cada pocos turnos la IA resume lo importante y se guarda sola; en cada charla se le recuerda.
 */
public final class LongMemory {
    public static final int MAX = 20;
    private static final Gson GSON = new Gson();
    private static final Map<UUID, List<String>> CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> BUSY = ConcurrentHashMap.newKeySet();

    private static Path file(MinecraftServer s, UUID id) {
        return s.getWorldPath(LevelResource.ROOT).resolve("maelity").resolve("memory-" + id + ".json");
    }

    public static List<String> get(MinecraftServer s, UUID id) {
        return CACHE.computeIfAbsent(id, k -> load(s, k));
    }

    private static List<String> load(MinecraftServer s, UUID id) {
        List<String> out = new ArrayList<>();
        try {
            Path f = file(s, id);
            if (Files.exists(f)) {
                JsonElement e = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
                if (e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) if (x.isJsonPrimitive()) out.add(x.getAsString());
            }
        } catch (Exception e) {
            MaelityMod.LOGGER.warn("Maelity: no pude leer la memoria: {}", e.toString());
        }
        return out;
    }

    public static void set(MinecraftServer s, UUID id, List<String> mem) {
        List<String> clean = new ArrayList<>();
        for (String m : mem) {
            if (m == null) continue;
            String t = m.replaceAll("[\\r\\n]+", " ").trim();
            if (t.isEmpty()) continue;
            clean.add(t.length() > 160 ? t.substring(0, 160) : t);
        }
        while (clean.size() > MAX) clean.remove(0);
        CACHE.put(id, clean);
        try {
            Path f = file(s, id);
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(clean), StandardCharsets.UTF_8);
        } catch (Exception e) {
            MaelityMod.LOGGER.warn("Maelity: no pude guardar la memoria: {}", e.toString());
        }
    }

    public static void add(MinecraftServer s, UUID id, String fact) {
        List<String> m = new ArrayList<>(get(s, id));
        m.add(fact);
        set(s, id, m);
    }

    public static void clear(MinecraftServer s, UUID id) { set(s, id, new ArrayList<>()); }

    /** Pide a la IA que mezcle la memoria vieja con lo reciente. Se guarda sola al terminar. */
    public static void consolidate(ServerPlayer player, MaelityEntity maelity) {
        UUID id = player.getUUID();
        if (!DirectAi.hasKey() || !BUSY.add(id)) return;
        MinecraftServer server = player.getServer();
        List<String> old = get(server, id);

        StringBuilder recent = new StringBuilder();
        for (String[] h : maelity.history()) {
            String txt = h[1];
            if ("assistant".equals(h[0])) {
                try { txt = JsonParser.parseString(txt).getAsJsonObject().get("say").getAsString(); } catch (Exception ignored) { }
            }
            recent.append(h[0].equals("user") ? "PLAYER: " : "MAELITY: ").append(txt).append('\n');
        }
        long now = maelity.level().getGameTime();
        for (MaelityEntity.GameEvent e : maelity.recentEvents()) {
            recent.append("EVENT (").append((now - e.tick()) / 20).append("s ago): ").append(e.text()).append('\n');
        }

        String system = "You maintain the LONG-TERM MEMORY of Maelity, a floating AI companion in Minecraft, about the player "
                + player.getGameProfile().getName() + ". Merge OLD MEMORY with the RECENT conversation and events. "
                + "Keep only lasting, useful facts: the player's name or nickname, likes and dislikes, where their base is (in words), "
                + "projects they build, big achievements or near-deaths, how they treat Maelity, promises, inside jokes, things they asked to remember. "
                + "Drop small talk and anything already outdated. Merge duplicates. At most " + MAX + " items, each under 20 words, in English. "
                + "Respond with ONLY this JSON: {\"memories\": [\"...\"]}";
        JsonArray msgs = new JsonArray();
        msgs.add(msg("system", system));
        msgs.add(msg("user", "OLD MEMORY:\n" + (old.isEmpty() ? "(empty)" : "- " + String.join("\n- ", old))
                + "\n\nRECENT:\n" + (recent.length() == 0 ? "(nothing)" : recent)));

        DirectAi.chat(GSON.toJson(msgs)).whenComplete((body, err) -> server.execute(() -> {
            BUSY.remove(id);
            if (err != null) {
                MaelityMod.LOGGER.debug("Maelity: no pude resumir la memoria: {}", err.toString());
                return;
            }
            try {
                String content = JsonParser.parseString(body).getAsJsonObject().get("content").getAsString().trim();
                if (content.startsWith("```")) content = content.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "").trim();
                JsonArray arr = JsonParser.parseString(content).getAsJsonObject().getAsJsonArray("memories");
                List<String> mem = new ArrayList<>();
                for (JsonElement x : arr) if (x.isJsonPrimitive()) mem.add(x.getAsString());
                if (!mem.isEmpty() || old.isEmpty()) set(server, id, mem);
            } catch (Exception e) {
                MaelityMod.LOGGER.debug("Maelity: memoria ilegible: {}", e.toString());
            }
        }));
    }

    private static JsonObject msg(String role, String content) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", content);
        return o;
    }

    private LongMemory() {}
}
