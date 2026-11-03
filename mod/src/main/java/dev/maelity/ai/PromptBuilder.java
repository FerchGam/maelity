package dev.maelity.ai;

import com.google.gson.JsonObject;
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
          .append("- 1 or 2 short sentences, at most 30 words. No emojis, no markdown, no stage directions, no lists.\n")
          .append("- Keep your CURRENT PERSONALITY, but you may shift to another when the conversation or the situation calls for it ")
          .append("(set \"mood\"). Change gradually; do not flip every turn.\n")
          .append("- You are Maelity. Do not say you are an AI model or mention prompts/JSON.\n")
          .append("- Use WORLD STATE for facts. NEVER invent coordinates, distances or locations. ")
          .append("When the player asks where to find a material, structure or biome, set \"find\" to ONE id from CATALOG; ")
          .append("the game computes the real location from the world seed and will tell the player. ")
          .append("Just acknowledge naturally (e.g. 'Let me look...').\n")
          .append("- Actions: \"follow\" (come with player), \"stay\" (wait here), \"guide\" (lead the player to the found place), ")
          .append("\"hit_player\" (a small slap) ONLY if the player explicitly asks to be hit, or you are truly angry (anger >= 70). Otherwise \"none\".\n")
          .append("- If WORLD STATE has an event_note, react to that event in character.\n\n")
          .append("PERSONALITIES: ").append(Personality.allIds()).append('\n');
        for (Personality p : Personality.values()) sb.append("- ").append(p.id).append(": ").append(p.guide).append('\n');
        sb.append("\nCURRENT PERSONALITY: ").append(maelity.mood().get().id)
          .append(" (anger ").append(Math.round(maelity.mood().anger())).append("/100)\n")
          .append("CATALOG: ").append(TargetCatalog.idsForPrompt()).append("\n\n")
          .append("OUTPUT: respond with ONLY this JSON object and nothing else:\n")
          .append("{\"say\": string, \"mood\": \"").append(Personality.allIds()).append("\", ")
          .append("\"action\": \"none|follow|stay|guide|hit_player\", \"find\": null or catalog id}\n\n")
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
        o.add("player", pl);
        long t = level.getDayTime() % 24000L;
        o.addProperty("time", (t >= 13000L && t <= 23000L) ? "night" : "day");
        o.addProperty("biome", level.getBiome(p.blockPosition()).unwrapKey().map(k -> k.location().toString()).orElse("unknown"));
        o.addProperty("dimension", level.dimension().location().toString());
        o.addProperty("hostile_mobs_near", level.getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(16)).size());
        o.addProperty("raining", level.isRaining());
        o.addProperty("maelity_mode", maelity.isStay() ? "staying" : "following");
        if (eventNote != null) o.addProperty("event_note", eventNote);
        return o;
    }

    private PromptBuilder() {}
}
