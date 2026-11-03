package dev.maelity.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Voz gratis y sin internet: usa las voces que ya trae Windows (System.Speech) via PowerShell.
 * Elige una voz del idioma (es/en) si esta instalada; si no, la predeterminada. Java puro.
 */
public final class WindowsTts {
    private static final String SCRIPT = String.join("\r\n",
            "Add-Type -AssemblyName System.Speech",
            "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer",
            "$v = $s.GetInstalledVoices() | Where-Object { $_.Enabled -and $_.VoiceInfo.Culture.TwoLetterISOLanguageName -eq $env:MAE_LANG } | Select-Object -First 1",
            "if ($v) { $s.SelectVoice($v.VoiceInfo.Name) }",
            "$s.Rate = [int]$env:MAE_RATE",
            "$s.SetOutputToWaveFile($env:MAE_OUT)",
            "$s.Speak((Get-Content -Raw -Encoding UTF8 -LiteralPath $env:MAE_TXT))",
            "$s.Dispose()");

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    /** Velocidad de la voz segun el humor (-10..10). */
    public static int rateFor(String mood) {
        if (mood == null) return 0;
        return switch (mood.toLowerCase(java.util.Locale.ROOT)) {
            case "playful" -> 2;
            case "angry" -> 3;
            case "mysterious" -> -3;
            case "sarcastic" -> -1;
            default -> 0;
        };
    }

    public static CompletableFuture<byte[]> synth(String text, String lang, String mood) {
        return CompletableFuture.supplyAsync(() -> {
            if (!supported()) throw new UnsupportedOperationException("Windows voices only");
            Path dir = null;
            try {
                dir = Files.createTempDirectory("maelity-tts");
                Path txt = dir.resolve("in.txt");
                Path out = dir.resolve("out.wav");
                Path ps1 = dir.resolve("speak.ps1");
                Files.writeString(txt, text, StandardCharsets.UTF_8);
                Files.writeString(ps1, SCRIPT, StandardCharsets.UTF_8);
                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                        "-ExecutionPolicy", "Bypass", "-File", ps1.toString());
                Map<String, String> env = pb.environment();
                env.put("MAE_LANG", "es".equals(lang) ? "es" : "en");
                env.put("MAE_RATE", Integer.toString(rateFor(mood)));
                env.put("MAE_OUT", out.toString());
                env.put("MAE_TXT", txt.toString());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                p.getInputStream().readAllBytes();
                if (!p.waitFor(25, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IllegalStateException("TTS timeout"); }
                if (!Files.exists(out) || Files.size(out) < 100) throw new IllegalStateException("TTS produced no audio");
                return Files.readAllBytes(out);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            } finally {
                if (dir != null) {
                    try (var s = Files.walk(dir)) {
                        s.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
                    } catch (Exception ignored) { }
                }
            }
        });
    }

    private WindowsTts() {}
}
