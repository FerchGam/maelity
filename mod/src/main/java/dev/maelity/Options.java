package dev.maelity;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.TranslatableEnum;

import java.util.Locale;

/** Opciones seleccionables de la configuracion (botones que cambian al hacer clic, sin escribir nada). */
public final class Options {
    private static Component name(String type, Enum<?> e) {
        return Component.translatable("maelity.configuration.enum." + type + "." + e.name().toLowerCase(Locale.ROOT));
    }

    public enum Lang implements TranslatableEnum {
        ES, EN;
        public String id() { return this == ES ? "es" : "en"; }
        public static Lang of(String s) { return "es".equalsIgnoreCase(s) ? ES : EN; }
        @Override public Component getTranslatedName() { return name("lang", this); }
    }

    public enum Shape implements TranslatableEnum {
        CUBE, BALL;
        @Override public Component getTranslatedName() { return name("shape", this); }
    }

    public enum FaceStyle implements TranslatableEnum {
        CLASSIC, CUTE, CAT, ROBOT, MINIMAL;
        public String id() { return name().toLowerCase(Locale.ROOT); }
        @Override public Component getTranslatedName() { return name("face", this); }
    }

    public enum VoiceEngine implements TranslatableEnum {
        PIPER, ELEVENLABS, FISH, WINDOWS;
        public String id() { return name().toLowerCase(Locale.ROOT); }
        @Override public Component getTranslatedName() { return name("engine", this); }
    }

    public enum Gender implements TranslatableEnum {
        FEMALE, MALE;
        @Override public Component getTranslatedName() { return name("gender", this); }
    }

    public enum PiperVoiceEs implements TranslatableEnum {
        AUTO(""), MX_CLAUDE("es_MX-claude-high"), MX_ALD("es_MX-ald-medium"), AR_DANIELA("es_AR-daniela-high"),
        ES_DAVEFX("es_ES-davefx-medium"), ES_SHARVARD("es_ES-sharvard-medium");
        public final String key;
        PiperVoiceEs(String key) { this.key = key; }
        @Override public Component getTranslatedName() { return name("piper_es", this); }
    }

    public enum PiperVoiceEn implements TranslatableEnum {
        AUTO(""), US_AMY("en_US-amy-medium"), US_RYAN("en_US-ryan-medium"), US_LESSAC("en_US-lessac-medium"),
        US_JOE("en_US-joe-medium"), GB_ALBA("en_GB-alba-medium");
        public final String key;
        PiperVoiceEn(String key) { this.key = key; }
        @Override public Component getTranslatedName() { return name("piper_en", this); }
    }

    public enum FishModel implements TranslatableEnum {
        FREE("s2.1-pro-free"), PRO("s2.1-pro");
        public final String id;
        FishModel(String id) { this.id = id; }
        @Override public Component getTranslatedName() { return name("fish_model", this); }
    }

    public enum FollowStyle implements TranslatableEnum {
        CALM, BEHIND;
        @Override public Component getTranslatedName() { return name("follow", this); }
    }

    public enum GiftTier implements TranslatableEnum {
        COMMON, RARE, EPIC, ADMIN;
        @Override public Component getTranslatedName() { return name("tier", this); }
    }

    public enum Color implements TranslatableEnum {
        YELLOW(0xFFD60A), GOLD(0xE6B422), ORANGE(0xFF8A1F), RED(0xE8402A), PINK(0xFF8FD8), MAGENTA(0xD63AF9),
        PURPLE(0x8A5CD6), BLUE(0x3D8BFF), CYAN(0x35D0E0), MINT(0x7FD4A8), GREEN(0x4CC94C), LIME(0xA6E22E),
        BROWN(0x8B5A2B), WHITE(0xF2F2F2), GRAY(0x8A8A8A), BLACK(0x2A2A2A);
        public final int rgb;
        Color(int rgb) { this.rgb = rgb; }
        @Override public Component getTranslatedName() { return name("color", this); }
    }

    private Options() {}
}
