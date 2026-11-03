package dev.maelity.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Catalogo cerrado de cosas que Maelity sabe buscar. Es Java puro (sin Minecraft) para poder
 * probarlo y para que el LLM solo pueda pedir ids validos (nunca inventa coordenadas).
 * Las palabras clave van SIN acentos y en minusculas.
 */
public final class TargetCatalog {
    public enum Kind { ORE, LOG, STRUCTURE, BIOME }

    public record Entry(String id, Kind kind, String dimension, String es, String en,
                        String bestY, List<String> keywords) {}

    public static final List<Entry> ALL;

    static {
        List<Entry> l = new ArrayList<>();
        // --- minerales / materiales (overworld)
        l.add(e("coal", Kind.ORE, "overworld", "carbon", "coal", "45", "carbon", "coal"));
        l.add(e("iron", Kind.ORE, "overworld", "hierro", "iron", "15", "hierro", "iron"));
        l.add(e("copper", Kind.ORE, "overworld", "cobre", "copper", "47", "cobre", "copper"));
        l.add(e("gold", Kind.ORE, "overworld", "oro", "gold", "-16", "oro", "gold"));
        l.add(e("redstone", Kind.ORE, "overworld", "redstone", "redstone", "-59", "redstone", "piedra roja"));
        l.add(e("lapis", Kind.ORE, "overworld", "lapislazuli", "lapis lazuli", "0", "lapis", "lapislazuli"));
        l.add(e("diamond", Kind.ORE, "overworld", "diamante", "diamond", "-59", "diamante", "diamond"));
        l.add(e("emerald", Kind.ORE, "overworld", "esmeralda", "emerald", "236", "esmeralda", "emerald"));
        l.add(e("wood", Kind.LOG, "overworld", "madera", "wood", "64", "madera", "tronco", "wood", "logs", "arbol", "tree"));
        // --- nether
        l.add(e("ancient_debris", Kind.ORE, "nether", "escombros antiguos", "ancient debris", "15",
                "escombros antiguos", "ancient debris", "netherita", "netherite"));
        l.add(e("quartz", Kind.ORE, "nether", "cuarzo", "nether quartz", "60", "cuarzo", "quartz"));
        l.add(e("nether_gold", Kind.ORE, "nether", "oro del nether", "nether gold", "60",
                "oro del nether", "oro nether", "nether gold"));
        // --- estructuras
        l.add(e("village", Kind.STRUCTURE, "overworld", "aldea", "village", "", "aldea", "pueblo", "village"));
        l.add(e("stronghold", Kind.STRUCTURE, "overworld", "fortaleza subterranea", "stronghold", "",
                "stronghold", "fortaleza subterranea", "portal del end", "end portal"));
        l.add(e("mansion", Kind.STRUCTURE, "overworld", "mansion del bosque", "woodland mansion", "",
                "mansion", "woodland mansion"));
        l.add(e("monument", Kind.STRUCTURE, "overworld", "monumento oceanico", "ocean monument", "",
                "monumento oceanico", "ocean monument"));
        l.add(e("desert_pyramid", Kind.STRUCTURE, "overworld", "piramide del desierto", "desert pyramid", "",
                "piramide", "desert pyramid", "desert temple", "templo del desierto"));
        l.add(e("jungle_temple", Kind.STRUCTURE, "overworld", "templo de la jungla", "jungle temple", "",
                "templo de la jungla", "templo de la selva", "jungle temple"));
        l.add(e("outpost", Kind.STRUCTURE, "overworld", "puesto de saqueadores", "pillager outpost", "",
                "puesto de saqueadores", "pillager outpost", "outpost"));
        l.add(e("ancient_city", Kind.STRUCTURE, "overworld", "ciudad antigua", "ancient city", "",
                "ciudad antigua", "ancient city"));
        l.add(e("trial_chambers", Kind.STRUCTURE, "overworld", "camaras de prueba", "trial chambers", "",
                "camaras de prueba", "camara de prueba", "trial chamber"));
        l.add(e("mineshaft", Kind.STRUCTURE, "overworld", "mina abandonada", "abandoned mineshaft", "",
                "mina abandonada", "mineshaft", "abandoned mine"));
        l.add(e("shipwreck", Kind.STRUCTURE, "overworld", "naufragio", "shipwreck", "",
                "naufragio", "barco hundido", "shipwreck"));
        l.add(e("ruined_portal", Kind.STRUCTURE, "overworld", "portal en ruinas", "ruined portal", "",
                "portal en ruinas", "ruined portal"));
        l.add(e("igloo", Kind.STRUCTURE, "overworld", "iglu", "igloo", "", "iglu", "igloo"));
        l.add(e("swamp_hut", Kind.STRUCTURE, "overworld", "cabana de bruja", "witch hut", "",
                "cabana de bruja", "witch hut", "swamp hut"));
        l.add(e("trail_ruins", Kind.STRUCTURE, "overworld", "ruinas de sendero", "trail ruins", "",
                "ruinas de sendero", "trail ruins"));
        l.add(e("fortress", Kind.STRUCTURE, "nether", "fortaleza del nether", "nether fortress", "",
                "fortaleza del nether", "nether fortress", "fortress"));
        l.add(e("bastion", Kind.STRUCTURE, "nether", "bastion", "bastion remnant", "", "bastion"));
        l.add(e("end_city", Kind.STRUCTURE, "end", "ciudad del end", "end city", "", "ciudad del end", "end city"));
        // --- biomas
        l.add(e("cherry_grove", Kind.BIOME, "overworld", "cerezos", "cherry grove", "", "cerezo", "cherry grove", "cherry"));
        l.add(e("mushroom_fields", Kind.BIOME, "overworld", "isla de hongos", "mushroom island", "",
                "isla de hongos", "mushroom", "champinon"));
        l.add(e("jungle", Kind.BIOME, "overworld", "jungla", "jungle", "", "jungla", "selva", "jungle"));
        // OJO: la palabra suelta "mesa" NO va aqui: "necesito una mesa de crafteo" disparaba la busqueda del bioma.
        l.add(e("badlands", Kind.BIOME, "overworld", "tierras baldias", "badlands", "",
                "tierras baldias", "bioma mesa", "badlands"));
        l.add(e("ice_spikes", Kind.BIOME, "overworld", "picos de hielo", "ice spikes", "",
                "picos de hielo", "ice spikes"));
        l.add(e("deep_dark", Kind.BIOME, "overworld", "oscuridad profunda", "deep dark", "",
                "oscuridad profunda", "deep dark"));
        l.add(e("lush_caves", Kind.BIOME, "overworld", "cuevas frondosas", "lush caves", "",
                "cuevas frondosas", "lush caves"));
        l.add(e("dripstone_caves", Kind.BIOME, "overworld", "cuevas de espeleotemas", "dripstone caves", "",
                "espeleotema", "dripstone"));
        l.add(e("mangrove_swamp", Kind.BIOME, "overworld", "manglar", "mangrove swamp", "", "manglar", "mangrove"));
        ALL = List.copyOf(l);
    }

    private static Entry e(String id, Kind k, String dim, String es, String en, String bestY, String... kw) {
        return new Entry(id, k, dim, es, en, bestY, List.of(kw));
    }

    public static Entry byId(String id) {
        if (id == null) return null;
        for (Entry x : ALL) if (x.id().equals(id)) return x;
        return null;
    }

    /** Lista de ids para el prompt del modelo. */
    public static String idsForPrompt() {
        StringBuilder sb = new StringBuilder();
        for (Entry x : ALL) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(x.id());
        }
        return sb.toString();
    }

    private TargetCatalog() {}
}
