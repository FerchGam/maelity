package dev.maelity.core;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Voces en la nube con la clave de cada jugador: ElevenLabs y Fish Audio. Devuelven WAV. Java puro. */
public final class CloudTts {
    public static final class TtsHttpException extends IOException {
        public final int status;
        public TtsHttpException(String who, int status, String body) {
            super(who + " HTTP " + status + ": " + (body.length() > 200 ? body.substring(0, 200) : body));
            this.status = status;
        }
    }

    public static final String ELEVEN_FEMALE = "EXAVITQu4vr4xnSDxMaL";   // "Sarah" (voz prediseñada)
    public static final String ELEVEN_MALE = "JBFqnCBsd6RMkjVDRZzb";     // "George" (voz prediseñada)
    public static final String ELEVEN_MODEL = "eleven_flash_v2_5";       // multilingue (es/en), gasta menos creditos

    public static CompletableFuture<byte[]> elevenLabs(HttpClient http, String apiKey, String voiceId, String text) {
        if (apiKey == null || apiKey.isBlank()) return CompletableFuture.failedFuture(new IOException("ElevenLabs key missing"));
        String json = "{\"text\":\"" + BackendClient.jsonEscape(text) + "\",\"model_id\":\"" + ELEVEN_MODEL + "\"}";
        HttpRequest r = HttpRequest.newBuilder(URI.create("https://api.elevenlabs.io/v1/text-to-speech/"
                        + URLEncoder.encode(voiceId, StandardCharsets.UTF_8) + "?output_format=pcm_22050"))
                .timeout(Duration.ofSeconds(30))
                .header("xi-api-key", apiKey.trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
        return http.sendAsync(r, HttpResponse.BodyHandlers.ofByteArray()).thenApply(resp -> {
            if (resp.statusCode() != 200) throw new CompletionException(
                    new TtsHttpException("ElevenLabs", resp.statusCode(), new String(resp.body(), StandardCharsets.UTF_8)));
            return Wav.fromPcm16(resp.body(), 22050, 1);
        });
    }

    /** voiceId vacio = voz por defecto de Fish Audio. model: "s2.1-pro-free" (gratis por tiempo limitado), "s2.1-pro"... */
    public static CompletableFuture<byte[]> fishAudio(HttpClient http, String apiKey, String model, String voiceId, String text) {
        if (apiKey == null || apiKey.isBlank()) return CompletableFuture.failedFuture(new IOException("Fish Audio key missing"));
        StringBuilder json = new StringBuilder("{\"text\":\"").append(BackendClient.jsonEscape(text)).append("\",\"format\":\"wav\"");
        if (voiceId != null && !voiceId.isBlank()) json.append(",\"reference_id\":\"").append(BackendClient.jsonEscape(voiceId.trim())).append('"');
        json.append('}');
        HttpRequest r = HttpRequest.newBuilder(URI.create("https://api.fish.audio/v1/tts"))
                .timeout(Duration.ofSeconds(40))
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "application/json")
                .header("model", model == null || model.isBlank() ? "s2.1-pro-free" : model.trim())
                .POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8)).build();
        return http.sendAsync(r, HttpResponse.BodyHandlers.ofByteArray()).thenApply(resp -> {
            if (resp.statusCode() != 200) throw new CompletionException(
                    new TtsHttpException("Fish Audio", resp.statusCode(), new String(resp.body(), StandardCharsets.UTF_8)));
            if (!Wav.isWav(resp.body())) throw new CompletionException(new IOException("Fish Audio did not return WAV"));
            return resp.body();
        });
    }

    private CloudTts() {}
}
