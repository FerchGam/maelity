package dev.maelity.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Voz neuronal gratis y sin internet (Piper). La primera vez descarga el programa (Windows) y la voz elegida
 * desde sus paginas oficiales y los guarda en la carpeta del juego. Java puro.
 */
public final class PiperTts {
    public static final String WINDOWS_ZIP =
            "https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_windows_amd64.zip";
    private static final String VOICES = "https://huggingface.co/rhasspy/piper-voices/resolve/main/";
    private static final Pattern VOICE_KEY = Pattern.compile("[a-z]{2,3}_[A-Z]{2}-[A-Za-z0-9_]+-(x_low|low|medium|high)");

    private final Path dir;
    private final HttpClient http;

    public PiperTts(Path dir, HttpClient http) {
        this.dir = dir;
        this.http = http;
    }

    public static boolean validVoice(String key) { return key != null && VOICE_KEY.matcher(key).matches(); }

    /** "es_MX-claude-high" -> https://huggingface.co/rhasspy/piper-voices/resolve/main/es/es_MX/claude/high/es_MX-claude-high.onnx */
    public static String voiceUrl(String key, String ext) {
        if (!validVoice(key)) throw new IllegalArgumentException("bad Piper voice: " + key);
        int a = key.indexOf('-');
        int z = key.lastIndexOf('-');
        String locale = key.substring(0, a);
        String name = key.substring(a + 1, z);
        String quality = key.substring(z + 1);
        String lang = locale.substring(0, locale.indexOf('_'));
        return VOICES + lang + "/" + locale + "/" + name + "/" + quality + "/" + key + ext;
    }

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private Path findExe() throws IOException {
        Path bin = dir.resolve("bin");
        if (!Files.isDirectory(bin)) return null;
        try (Stream<Path> s = Files.walk(bin, 4)) {
            return s.filter(p -> p.getFileName().toString().equalsIgnoreCase("piper.exe")).findFirst().orElse(null);
        }
    }

    /** Programa listo para usar (lo descarga la primera vez). */
    public synchronized Path ensureExe(Consumer<String> status) throws Exception {
        if (!supported()) throw new UnsupportedOperationException("Piper download is only set up for Windows");
        Path exe = findExe();
        if (exe != null) return exe;
        status.accept("program");
        Files.createDirectories(dir);
        Path zip = download(WINDOWS_ZIP, dir.resolve("piper.zip.part"));
        Path bin = dir.resolve("bin");
        Files.createDirectories(bin);
        unzip(zip, bin);
        Files.deleteIfExists(zip);
        exe = findExe();
        if (exe == null) throw new IOException("piper.exe not found after unzip");
        return exe;
    }

    /** Voz lista para usar (la descarga la primera vez). */
    public synchronized Path ensureVoice(String key, Consumer<String> status) throws Exception {
        Path vdir = dir.resolve("voices");
        Path onnx = vdir.resolve(key + ".onnx");
        Path json = vdir.resolve(key + ".onnx.json");
        if (Files.exists(onnx) && Files.exists(json) && Files.size(onnx) > 1_000_000) return onnx;
        status.accept("voice");
        Files.createDirectories(vdir);
        Files.move(download(voiceUrl(key, ".onnx.json"), vdir.resolve(key + ".json.part")), json, StandardCopyOption.REPLACE_EXISTING);
        Files.move(download(voiceUrl(key, ".onnx"), vdir.resolve(key + ".onnx.part")), onnx, StandardCopyOption.REPLACE_EXISTING);
        return onnx;
    }

    private Path download(String url, Path target) throws Exception {
        HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5))
                .header("User-Agent", "Maelity-Minecraft-mod").GET().build();
        HttpResponse<Path> resp = http.send(r, HttpResponse.BodyHandlers.ofFile(target));
        if (resp.statusCode() != 200) {
            Files.deleteIfExists(target);
            throw new IOException("download failed HTTP " + resp.statusCode() + ": " + url);
        }
        return target;
    }

    /** Descomprime evitando rutas peligrosas ("zip slip"). */
    static void unzip(Path zip, Path dest) throws IOException {
        Path root = dest.toAbsolutePath().normalize();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                Path out = root.resolve(e.getName()).normalize();
                if (!out.startsWith(root)) throw new IOException("bad zip entry: " + e.getName());
                if (e.isDirectory()) { Files.createDirectories(out); continue; }
                Files.createDirectories(out.getParent());
                try (OutputStream os = Files.newOutputStream(out)) { in.transferTo(os); }
            }
        }
    }

    /** Velocidad segun humor (Piper: length_scale > 1 = mas lento). */
    public static double lengthScaleFor(String mood) {
        if (mood == null) return 1.0;
        return switch (mood.toLowerCase(Locale.ROOT)) {
            case "playful", "angry" -> 0.88;
            case "mysterious" -> 1.2;
            case "sarcastic" -> 1.05;
            default -> 1.0;
        };
    }

    public byte[] synth(String text, String voiceKey, String mood, Consumer<String> status) throws Exception {
        Path exe = ensureExe(status);
        Path model = ensureVoice(voiceKey, status);
        Path out = Files.createTempFile("maelity-piper", ".wav");
        try {
            ProcessBuilder pb = new ProcessBuilder(exe.toString(), "--model", model.toString(),
                    "--output_file", out.toString(), "--length_scale", String.valueOf(lengthScaleFor(mood)));
            pb.directory(exe.getParent().toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (OutputStream os = p.getOutputStream()) {
                os.write(text.replace('\n', ' ').getBytes(StandardCharsets.UTF_8));
                os.write('\n');
            }
            try (InputStream is = p.getInputStream()) { is.readAllBytes(); }
            if (!p.waitFor(40, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IOException("piper timeout"); }
            if (!Files.exists(out) || Files.size(out) < 100) throw new IOException("piper produced no audio (exit " + p.exitValue() + ")");
            return Files.readAllBytes(out);
        } finally {
            Files.deleteIfExists(out);
        }
    }
}
