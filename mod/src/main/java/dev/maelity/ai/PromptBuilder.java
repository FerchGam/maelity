package dev.maelity.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import dev.maelity.core.TargetCatalog;
import dev.maelity.entity.MaelityEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;

public final class PromptBuilder {

    public static String system(MaelityEntity maelity, ServerPlayer player, String lang, String extraNote) {
        String language = "es".equals(lang) ? "Spanish (es)" : "English (en)";
        StringBuilder sb = new StringBuilder(2200);
        sb.append("You are Maelity, a small floating yellow companion that lives in the player's Minecraft world. ")
          .append("You talk to ONE player by voice, so write only what should be spoken aloud.\n\n")
          .append("RULES\n")
          .append("- Reply ONLY in ").append(language).append(". Never mix languages.\n")
          .append("- 1 or 2 short sentences, at most 35 words. No emojis, no markdown, no stage directions, no lists.\n")
          .append("- Keep your CURRENT PERSONALITY, but you may shift to another when the conversation or the situation calls for it ")
          .append("(set \"mood\"). Change gradually; do not flip every turn.\n")
          .append("- You are Maelity. Do not say you are an AI model or mention prompts/JSON.\n")
          .append("- Use WORLD STATE for facts. NEVER invent coordinates, distances or locations. ")
          .append("When the player asks where to find a material, structure or biome, set \"find\" to ONE id from CATALOG; ")
          .append("the game computes the real location from the world seed and will tell the player. ")
          .append("Just acknowledge naturally (e.g. 'Let me look...').\n")
          .append("- Actions: \"follow\" (come with player), \"stay\" (wait here), \"guide\" (fly ahead and lead the player to WORLD STATE.last_found_place; use it whenever the player accepts your offer to guide them, e.g. they say yes), ")
          .append("\"defend\" (attack the nearest monster near the player), \"flee\" (fly away from danger), \"move_aside\" (get out of the player's way), ")
          .append("\"hit_player\" (a small slap) ONLY if the player explicitly asks to be hit, or you are truly angry (anger >= 70). Otherwise \"none\".\n")
          .append("- BE SMART: read WORLD STATE (health, food, inventory, threats, recent_events, time, place) and give concrete, useful ")
          .append("Minecraft advice (what to craft, where to go, what to eat, how to survive). Notice problems before the player does. ")
          .append("Remember what happened in recent_events and refer to it naturally.\n")
          .append("- GIFTS: you can conjure ANY item like in creative mode. Set \"give\" to {\"item\": \"minecraft:<item_id>\", \"count\": n} ")
          .append("when you WANT to give something (the player asks, or they clearly need it). Whether you give depends on your mood and affection: ")
          .append("friendly is generous; playful may prank with a silly item; protective gives survival items (food, golden apples, shields, totems in real danger); ")
          .append("sarcastic only gives cheap things and complains; mysterious gives odd or cryptic things; angry refuses. ")
          .append("Check WORLD STATE.maelity.can_give before offering: never promise a tier you cannot give now. Use exact vanilla item ids. ")
          .append("Never give command blocks, spawn eggs, barriers or other admin items. Otherwise \"give\": null.\n")
          .append("- COMBAT: the game decides automatically whether you defend the player or flee, and tells you with an event_note. ")
          .append("When the player asks you to fight or run, use \"defend\" or \"flee\".\n")
          .append("- If WORLD STATE has an event_note, react to that event in character.\n")
          .append("- LONG-TERM MEMORY holds what you remember from earlier sessions. Use it naturally (callbacks, inside jokes, ")
          .append("their name), but never recite the list.\n\n")
          .append("PERSONALITIES: ").append(Personality.allIds()).append('\n');
        for (Personality p : Personality.values()) sb.append("- ").append(p.id).append(": ").append(p.guide).append('\n');
        sb.append("\nCURRENT PERSONALITY: ").append(maelity.mood().get().id)
          .append(" (anger ").append(Math.round(maelity.mood().anger())).append("/100)\n")
          .append("CATALOG: ").append(TargetCatalog.idsForPrompt()).append("\n\n")
          .append("OUTPUT: respond with ONLY this JSON object and nothing else:\n")
          .append("{\"say\": string, \"mood\": \"").append(Personality.allIds()).append("\", ")
          .append("\"action\": \"none|follow|stay|guide|defend|flee|move_aside|hit_player\", \"find\": null or catalog id, ")
          .append("\"give\": null or {\"item\": \"minecraft:id\", \"count\": number}}\n\n")
          .append(memoryBlock(player))
          .append("WORLD STATE: ").append(worldState(maelity, player, extraNote).toString());
        return sb.toString();
    }

    private static JsonObject worldState(MaelityEntity maelity, ServerPlayer p, String eventNote) {
        ServerLevel level = p.serverLevel();
        JsonObject o = new JsonObject();
        JsonObject pl = new JsonObject();
        pl.addProperty("name", p.getGameProfile().getName());
        pl.addProperty("health", Math.round(p.getHealth() * 10) / 10.0);
        pl.addProperty("max_health", Math.round(p.getMaxHealth()));
        pl.addProperty("food", p.getFoodData().getFoodLevel());
        pl.addProperty("holding", p.getMainHandItem().isEmpty() ? "nothing" : p.getMainHandItem().getHoverName().getString());
        pl.addProperty("armor", p.getArmorValue());
        pl.addProperty("xp_level", p.experienceLevel);
        pl.addProperty("game_mode", p.gameMode.getGameModeForPlayer().getName());
        pl.addProperty("y", (int) Math.floor(p.getY()));
        pl.addProperty("underground", !level.canSeeSky(p.blockPosition()));
        pl.addProperty("light", level.getMaxLocalRawBrightness(p.blockPosition()));
        pl.add("inventory", inventory(p));
        o.add("player", pl);
        long t = level.getDayTime() % 24000L;
        o.addProperty("time", (t >= 13000L && t <= 23000L) ? "night" : "day");
        o.addProperty("biome", level.getBiome(p.blockPosition()).unwrapKey().map(k -> k.location().toString()).orElse("unknown"));
        o.addProperty("dimension", level.dimension().location().toString());
        o.addProperty("hostile_mobs_near", level.getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(16)).size());
        JsonObject near = new JsonObject();
        for (Monster m : level.getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(16))) {
            String k = BuiltInRegistries.ENTITY_TYPE.getKey(m.getType()).getPath();
            near.addProperty(k, near.has(k) ? near.get(k).getAsInt() + 1 : 1);
        }
        o.add("hostile_kinds", near);
        String threats = maelity.threatSummary();
        if (!threats.isEmpty()) o.addProperty("attacking_player_now", threats);
        o.addProperty("raining", level.isRaining());
        o.addProperty("maelity_mode", maelity.isStay() ? "staying" : "following");
        JsonObject me = new JsonObject();
        me.addProperty("health", Math.round(maelity.getHealth()) + "/" + Math.round(maelity.getMaxHealth()));
        me.addProperty("affection_for_player", Math.round(maelity.mood().affection()));
        me.addProperty("combat", maelity.combatState());
        me.addProperty("glowing_light", maelity.isLit());
        long[] cd = maelity.giftCooldownLeft(level.getGameTime());
        JsonObject can = new JsonObject();
        can.addProperty("common_items", cd[0] == 0);
        can.addProperty("rare_items", cd[1] == 0);
        can.addProperty("epic_items", cd[2] == 0);
        me.add("can_give", can);
        o.add("maelity", me);
        if (maelity.lastFound() != null) {
            JsonObject lf = new JsonObject();
            lf.addProperty("place", maelity.lastFoundName());
            lf.addProperty("distance", (int) Math.round(Math.sqrt(maelity.lastFound().distToCenterSqr(p.getX(), maelity.lastFound().getY(), p.getZ()))));
            lf.addProperty("guiding_now", maelity.getGuideTarget() != null);
            o.add("last_found_place", lf);
        }
        JsonArray ev = new JsonArray();
        long now = level.getGameTime();
        for (MaelityEntity.GameEvent e : maelity.recentEvents()) ev.add(((now - e.tick()) / 20) + "s ago: " + e.text());
        if (ev.size() > 0) o.add("recent_events", ev);
        if (eventNote != null) o.addProperty("event_note", eventNote);
        return o;
    }

    private static String memoryBlock(ServerPlayer p) {
        java.util.List<String> mem = LongMemory.get(p.getServer(), p.getUUID());
        if (mem.isEmpty()) return "LONG-TERM MEMORY: (nothing yet, you are just getting to know this player)\n\n";
        StringBuilder sb = new StringBuilder("LONG-TERM MEMORY:\n");
        for (String m : mem) sb.append("- ").append(m).append('\n');
        return sb.append('\n').toString();
    }

    /** Resumen del inventario: los objetos mas abundantes (para que de consejos con sentido). */
    private static JsonObject inventory(ServerPlayer p) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack st : p.getInventory().items) {
            if (st.isEmpty()) continue;
            counts.merge(BuiltInRegistries.ITEM.getKey(st.getItem()).getPath(), st.getCount(), Integer::sum);
        }
        JsonObject inv = new JsonObject();
        counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(14)
                .forEach(e -> inv.addProperty(e.getKey(), e.getValue()));
        return inv;
    }

    private PromptBuilder() {}
}
