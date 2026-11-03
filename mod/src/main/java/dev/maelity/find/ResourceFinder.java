package dev.maelity.find;

import com.mojang.datafixers.util.Pair;
import dev.maelity.MaelityConfig;
import dev.maelity.MaelityMod;
import dev.maelity.ai.MaelityBrain;
import dev.maelity.core.Compass;
import dev.maelity.core.TargetCatalog;
import dev.maelity.core.TargetCatalog.Entry;
import dev.maelity.core.TargetCatalog.Kind;
import dev.maelity.entity.MaelityEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Busca materiales segun la SEMILLA REAL del mundo (no hace trampa leyendo solo lo ya explorado):
 *  - Estructuras: el mismo localizador que /locate structure (calculo puro de la semilla).
 *  - Biomas: el mismo que /locate biome.
 *  - Minerales / madera: genera con el generador del mundo los chunks cercanos hasta el estado FEATURES
 *    (los minerales ya estan colocados) y los escanea, de cerca a lejos, repartido por ticks.
 *    Efecto secundario: esos chunks quedan generados (se habrian generado igual al explorar).
 */
public final class ResourceFinder {
    public enum Status { FOUND, NOT_FOUND, WRONG_DIMENSION }

    public record Result(Status status, BlockPos pos, boolean hasY, double distance, int searchedBlocks) {}

    private static final Map<UUID, Job> JOBS = new HashMap<>();

    public static boolean hasJob(UUID player) { return JOBS.containsKey(player); }

    // ------------------------------------------------------------------ inicio

    public static void start(ServerPlayer player, MaelityEntity maelity, Entry target, boolean guide, String lang) {
        ServerLevel level = player.serverLevel();
        if (!dimensionMatches(level, target)) {
            report(player, maelity, target, new Result(Status.WRONG_DIMENSION, BlockPos.ZERO, false, 0, 0), guide, lang);
            return;
        }
        if (target.kind() == Kind.STRUCTURE || target.kind() == Kind.BIOME) {
            // Un solo calculo, igual que /locate: puede tardar un instante, ocurre en el hilo del servidor.
            Result r;
            try {
                r = target.kind() == Kind.STRUCTURE ? locateStructure(level, player, target) : locateBiome(level, player, target);
            } catch (Exception e) {
                MaelityMod.LOGGER.warn("Maelity locate fallo para {}: {}", target.id(), e.toString());
                r = new Result(Status.NOT_FOUND, BlockPos.ZERO, false, 0, 6400);
            }
            report(player, maelity, target, r, guide, lang);
            return;
        }
        JOBS.put(player.getUUID(), new Job(player, maelity, target, guide, lang, level));
    }

    // ------------------------------------------------------------------ tick (spread)

    public static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Job>> it = JOBS.entrySet().iterator();
        while (it.hasNext()) {
            Job job = it.next().getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(job.playerId);
            if (p == null || job.maelity.isRemoved() || p.serverLevel() != job.level || ++job.ticks > 20 * 240) {
                it.remove();
                continue;
            }
            Result res;
            try {
                res = job.step();
            } catch (Exception e) {
                MaelityMod.LOGGER.warn("Maelity escaneo fallo: {}", e.toString());
                res = new Result(Status.NOT_FOUND, BlockPos.ZERO, false, 0, job.radiusBlocks());
            }
            if (res != null) {
                it.remove();
                report(p, job.maelity, job.target, res, job.guide, job.lang);
            }
        }
    }

    // ------------------------------------------------------------------ estructuras y biomas

    private static Result locateStructure(ServerLevel level, ServerPlayer player, Entry target) {
        Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<Holder<Structure>> holders = new ArrayList<>();
        for (String id : structureIds(target.id())) {
            Optional<Holder.Reference<Structure>> h =
                    reg.getHolder(ResourceKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace(id)));
            h.ifPresent(holders::add);
        }
        if (holders.isEmpty()) return new Result(Status.NOT_FOUND, BlockPos.ZERO, false, 0, 0);
        HolderSet<Structure> set = HolderSet.direct(holders);
        Pair<BlockPos, Holder<Structure>> r = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, set, player.blockPosition(), 100, false);
        if (r == null) return new Result(Status.NOT_FOUND, BlockPos.ZERO, false, 0, 1600);
        BlockPos pos = r.getFirst();
        return new Result(Status.FOUND, pos, false, horizDist(player.blockPosition(), pos), 0);
    }

    private static Result locateBiome(ServerLevel level, ServerPlayer player, Entry target) {
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace(target.id()));
        Pair<BlockPos, Holder<Biome>> r = level.findClosestBiome3d(h -> h.is(key), player.blockPosition(), 6400, 32, 64);
        if (r == null) return new Result(Status.NOT_FOUND, BlockPos.ZERO, false, 0, 6400);
        BlockPos pos = r.getFirst();
        return new Result(Status.FOUND, pos, true, horizDist(player.blockPosition(), pos), 0);
    }

    private static List<String> structureIds(String id) {
        return switch (id) {
            case "village" -> List.of("village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga");
            case "desert_pyramid" -> List.of("desert_pyramid");
            case "jungle_temple" -> List.of("jungle_pyramid");
            case "outpost" -> List.of("pillager_outpost");
            case "mineshaft" -> List.of("mineshaft", "mineshaft_mesa");
            case "shipwreck" -> List.of("shipwreck", "shipwreck_beached");
            case "ruined_portal" -> List.of("ruined_portal", "ruined_portal_desert", "ruined_portal_jungle",
                    "ruined_portal_swamp", "ruined_portal_mountain", "ruined_portal_ocean");
            case "bastion" -> List.of("bastion_remnant");
            case "monument" -> List.of("monument");
            default -> List.of(id); // stronghold, mansion, ancient_city, trial_chambers, igloo, swamp_hut, trail_ruins, fortress, end_city
        };
    }

    private static boolean dimensionMatches(ServerLevel level, Entry t) {
        String d = level.dimension().location().getPath();
        return switch (t.dimension()) {
            case "nether" -> d.equals("the_nether");
            case "end" -> d.equals("the_end");
            default -> d.equals("overworld");
        };
    }

    // ------------------------------------------------------------------ escaneo de chunks

    private static final class Job {
        final UUID playerId;
        final MaelityEntity maelity;
        final Entry target;
        final boolean guide;
        final String lang;
        final ServerLevel level;
        final BlockPos origin;
        final Predicate<BlockState> match;
        final int minY, maxY;
        final List<int[]> order = new ArrayList<>();
        final ChunkPos originChunk;
        int idx = 0;
        int ticks = 0;
        BlockPos best;
        double bestDistSq = Double.MAX_VALUE;

        Job(ServerPlayer p, MaelityEntity a, Entry t, boolean guide, String lang, ServerLevel level) {
            this.playerId = p.getUUID();
            this.maelity = a;
            this.target = t;
            this.guide = guide;
            this.lang = lang;
            this.level = level;
            this.origin = p.blockPosition();
            this.originChunk = new ChunkPos(origin);
            int[] range = yRange(t.id());
            this.minY = Math.max(range[0], level.getMinBuildHeight());
            this.maxY = Math.min(range[1], level.getMaxBuildHeight() - 1);
            this.match = predicate(t.id());
            int r = MaelityConfig.COMMON.scanRadiusChunks.get();
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++)
                    if (dx * dx + dz * dz <= r * r) order.add(new int[]{dx, dz});
            order.sort(Comparator.comparingDouble(c -> lowerBoundSq(c[0], c[1])));
        }

        int radiusBlocks() { return MaelityConfig.COMMON.scanRadiusChunks.get() * 16; }

        /** Distancia minima posible (al cuadrado) a cualquier bloque de ese chunk, estando en el chunk origen. */
        static double lowerBoundSq(int dx, int dz) {
            double x = Math.max(0, (Math.abs(dx) - 1) * 16), z = Math.max(0, (Math.abs(dz) - 1) * 16);
            return x * x + z * z + (dx * dx + dz * dz) * 0.001;
        }

        Result step() {
            int per = MaelityConfig.COMMON.chunksPerTick.get();
            for (int n = 0; n < per; n++) {
                if (idx >= order.size()) return finish();
                int[] c = order.get(idx);
                if (best != null && lowerBoundSq(c[0], c[1]) > bestDistSq) return finish();
                idx++;
                // Genera (si hace falta) el chunk con las features ya colocadas: ahi estan los minerales de la semilla.
                ChunkAccess chunk = level.getChunk(originChunk.x + c[0], originChunk.z + c[1], ChunkStatus.FEATURES);
                scan(chunk);
            }
            return null;
        }

        Result finish() {
            if (best == null) return new Result(Status.NOT_FOUND, BlockPos.ZERO, false, 0, radiusBlocks());
            return new Result(Status.FOUND, best, true, Math.sqrt(bestDistSq), radiusBlocks());
        }

        void scan(ChunkAccess chunk) {
            LevelChunkSection[] sections = chunk.getSections();
            int minSection = chunk.getMinSection();
            int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
            for (int i = 0; i < sections.length; i++) {
                int baseY = (minSection + i) << 4;
                if (baseY + 15 < minY || baseY > maxY) continue;
                LevelChunkSection s = sections[i];
                if (s.hasOnlyAir() || !s.maybeHas(match)) continue;
                for (int y = 0; y < 16; y++) {
                    int wy = baseY + y;
                    if (wy < minY || wy > maxY) continue;
                    for (int z = 0; z < 16; z++)
                        for (int x = 0; x < 16; x++) {
                            if (!match.test(s.getBlockState(x, y, z))) continue;
                            double dx = bx + x - origin.getX(), dy = wy - origin.getY(), dz = bz + z - origin.getZ();
                            double d = dx * dx + dy * dy + dz * dz;
                            if (d < bestDistSq) {
                                bestDistSq = d;
                                best = new BlockPos(bx + x, wy, bz + z);
                            }
                        }
                }
            }
        }
    }

    private static int[] yRange(String id) {
        return switch (id) {
            case "coal" -> new int[]{0, 140};
            case "iron" -> new int[]{-64, 80};
            case "copper" -> new int[]{-16, 112};
            case "gold" -> new int[]{-64, 256};
            case "redstone" -> new int[]{-64, 16};
            case "lapis" -> new int[]{-64, 64};
            case "diamond" -> new int[]{-64, 20};
            case "emerald" -> new int[]{-16, 256};
            case "wood" -> new int[]{50, 200};
            case "ancient_debris" -> new int[]{0, 40};
            default -> new int[]{0, 127}; // quartz, nether_gold
        };
    }

    private static Predicate<BlockState> predicate(String id) {
        return switch (id) {
            case "coal" -> s -> s.is(BlockTags.COAL_ORES);
            case "iron" -> s -> s.is(BlockTags.IRON_ORES);
            case "copper" -> s -> s.is(BlockTags.COPPER_ORES);
            case "gold" -> s -> s.is(BlockTags.GOLD_ORES);
            case "redstone" -> s -> s.is(BlockTags.REDSTONE_ORES);
            case "lapis" -> s -> s.is(BlockTags.LAPIS_ORES);
            case "diamond" -> s -> s.is(BlockTags.DIAMOND_ORES);
            case "emerald" -> s -> s.is(BlockTags.EMERALD_ORES);
            case "wood" -> s -> s.is(BlockTags.LOGS);
            case "ancient_debris" -> s -> s.is(Blocks.ANCIENT_DEBRIS);
            case "quartz" -> s -> s.is(Blocks.NETHER_QUARTZ_ORE);
            case "nether_gold" -> s -> s.is(Blocks.NETHER_GOLD_ORE);
            default -> s -> false;
        };
    }

    // ------------------------------------------------------------------ informe

    private static double horizDist(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void report(ServerPlayer p, MaelityEntity maelity, Entry t, Result r, boolean guide, String lang) {
        boolean es = "es".equals(lang);
        String name = es ? t.es() : t.en();
        String fact;
        String note;
        switch (r.status()) {
            case FOUND -> {
                BlockPos pos = r.pos();
                String dir = Compass.direction(pos.getX() - p.getX(), pos.getZ() - p.getZ(), lang);
                int dist = (int) Math.round(r.distance());
                String coords = r.hasY() ? pos.getX() + ", " + pos.getY() + ", " + pos.getZ() : pos.getX() + ", ?, " + pos.getZ();
                boolean reveal = MaelityConfig.COMMON.revealCoordinates.get();
                String where = reveal ? (es ? " (X, Y, Z: " : " (X, Y, Z: ") + coords + ")" : "";
                fact = es ? name + ": a unos " + dist + " bloques al " + dir + where + "."
                          : name + ": about " + dist + " blocks " + dir + where + ".";
                note = "Search finished: found '" + t.id() + "' about " + dist + " blocks to the " + dir
                        + (r.hasY() ? ", at height Y=" + pos.getY() : "") + ". Tell the player the direction and distance"
                        + (reveal ? " (exact coordinates are shown in chat)." : " (do not give exact coordinates).")
                        + (guide ? " You will lead them there." : " Offer to guide them if they want.");
                if (reveal) MaelityBrain.tellPlayer(p, "§e[Maelity]§r " + fact);
                if (guide) maelity.setGuideTarget(pos);
            }
            case NOT_FOUND -> {
                String hint = t.bestY().isEmpty() ? "" : (es ? " Suelen abundar cerca de Y=" : " It is usually most common near Y=") + t.bestY() + ".";
                fact = es ? "No encontré " + name + " en " + r.searchedBlocks() + " bloques a la redonda. Prueba en otra zona." + hint
                          : "I couldn't find " + name + " within " + r.searchedBlocks() + " blocks. Try another area." + hint;
                note = "Search finished: '" + t.id() + "' NOT found within " + r.searchedBlocks() + " blocks of the player. Suggest exploring elsewhere."
                        + (t.bestY().isEmpty() ? "" : " Best height is about Y=" + t.bestY() + ".");
                if (MaelityConfig.COMMON.revealCoordinates.get()) MaelityBrain.tellPlayer(p, "§e[Maelity]§r " + fact);
            }
            default -> {
                String dim = switch (t.dimension()) { case "nether" -> es ? "el Nether" : "the Nether"; case "end" -> es ? "el End" : "the End"; default -> es ? "el Overworld" : "the Overworld"; };
                fact = es ? name + " solo se encuentra en " + dim + "." : name + " can only be found in " + dim + ".";
                note = "The player asked for '" + t.id() + "' but it only exists in " + dim + " and you are in another dimension. Tell them.";
            }
        }
        MaelityBrain.event(p, maelity, note, fact);
    }

    private ResourceFinder() {}
}
