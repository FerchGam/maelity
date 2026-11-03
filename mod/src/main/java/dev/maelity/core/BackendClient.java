package dev.maelity.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Cliente HTTP minimo hacia el backend de Maelity (chat / STT / TTS). Java puro, sin Gson:
 * devuelve texto o bytes crudos y deja el JSON a quien lo llama.
 */
public final class BackendClient {
    public static final class BackendException extends IOException {
        public final int status;
        public BackendException(int status, String body) {
            super("Backend HTTP " + status + ": " + (body.length() > 200 ? body.substring(0, 200) : body));
            this.status = status;
        }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private final String base;
    private final String token;

    public BackendClient(String baseUrl, String token) {
        this.base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token == null ? "" : token;
    }

    private HttpRequest.Builder req(String pathAndQuery, Duration timeout) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + pathAndQuery)).timeout(timeout);
        if (!token.isEmpty()) b.header("X-Maelity-Token", token);
        return b;
    }

    /** POST JSON -> cuerpo como texto. */
    public CompletableFuture<String> postJson(String path, String json, Duration timeout) {
        HttpRequest r = req(path, timeout).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
        return HTTP.sendAsync(r, HttpResponse.BodyHandlers.ofByteArray()).thenApply(resp -> {
            String body = new String(resp.body(), StandardCharsets.UTF_8);
            if (resp.statusCode() != 200) throw new java.util.concurrent.CompletionException(new BackendException(resp.statusCode(), body));
            return body;
        });
    }

    /** POST JSON -> cuerpo binario (audio). */
    public CompletableFuture<byte[]> postJsonForBytes(String path, String json, Duration timeout) {
        HttpRequest r = req(path, timeout).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
        return HTTP.sendAsync(r, HttpResponse.BodyHandlers.ofByteArray()).thenApply(resp -> {
            if (resp.statusCode() != 200)
                throw new java.util.concurrent.CompletionException(
                        new BackendException(resp.statusCode(), new String(resp.body(), StandardCharsets.UTF_8)));
            return resp.body();
        });
    }

    /** POST binario (WAV) -> texto. */
    public CompletableFuture<String> postBytes(String pathAndQuery, byte[] body, String contentType, Duration timeout) {
        HttpRequest r = req(pathAndQuery, timeout).header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        return HTTP.sendAsync(r, HttpResponse.BodyHandlers.ofByteArray()).thenApply(resp -> {
            String s = new String(resp.body(), StandardCharsets.UTF_8);
            if (resp.statusCode() != 200) throw new java.util.concurrent.CompletionException(new BackendException(resp.statusCode(), s));
            return s;
        });
    }

    // ---- API de alto nivel (JSON armado a mano para no depender de Gson aqui)

    public CompletableFuture<String> chat(String messagesJsonArray, String lang) {
        return postJson("/v1/chat", "{\"lang\":\"" + lang + "\",\"messages\":" + messagesJsonArray + "}", Duration.ofSeconds(25));
    }

    public CompletableFuture<String> stt(byte[] wav, String lang) {
        return postBytes("/v1/stt?lang=" + lang, wav, "audio/wav", Duration.ofSeconds(25));
    }

    public CompletableFuture<byte[]> tts(String text, String lang, String mood) {
        String json = "{\"lang\":\"" + lang + "\",\"mood\":\"" + jsonEscape(mood) + "\",\"text\":\"" + jsonEscape(text) + "\"}";
        return postJsonForBytes("/v1/tts", json, Duration.ofSeconds(25));
    }

    public static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
