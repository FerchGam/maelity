package dev.maelity.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.maelity.MaelityConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

/**
 * IA directa: el mod habla con Groq usando la clave GRATIS de cada jugador (sin servidor propio).
 * Mismo formato de respuesta que el backend antiguo: chat -> {"content": "..."}, stt -> {"text": "..."}.
 */
public final class DirectAi {
    private static final String BASE = "https://api.groq.com/openai/v1";
    private static final String CHAT_MODEL = "llama-3.3-70b-versatile";
    private static final String STT_MODEL = "whisper-large-v3-turbo";

    public static final class NoKeyException extends IOException {
        public NoKeyException() { super("No Groq API key configured"); }
    }

    public static final class HttpStatusException extends IOException {
        public final int status;
        public HttpStatusException(int status, String body) {
            super("Groq HTTP " + status + ": " + (body.length() > 200 ? body.substring(0, 200) : body));
            this.status = status;
        }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private static final List<Pattern> HALLUCINATIONS = List.of(
            Pattern.compile("amara\\.org", Pattern.CASE_INSENSITIVE),
            Pattern.compile("subt[ií]tulos", Pattern.CASE_INSENSITIVE),
            Pattern.compile("thanks for watching", Pattern.CASE_INSENSITIVE),
            Pattern.compile("gracias por ver", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\W*$"));

    public static boolean hasKey() { return !key().isEmpty(); }

    private static String key() {
        try { return MaelityConfig.COMMON.groqApiKey.get().trim(); } catch (Exception e) { return ""; }
    }

    private static HttpRequest.Builder req(String path, Duration timeout) throws NoKeyException {
        String k = key();
        if (k.isEmpty()) throw new NoKeyException();
        return HttpRequest.newBuilder(URI.create(BASE + path)).timeout(timeout).header("Authorization", "Bearer " + k);
    }

    private static CompletableFuture<String> send(HttpRequest r) {
        return HTTP.sendAsync(r, HttpResponse.BodyHandlers.ofByteArray()).thenApply(resp -> {
            String body = new String(resp.body(), StandardCharsets.UTF_8);
            if (resp.statusCode() != 200) throw new CompletionException(new HttpStatusException(resp.statusCode(), body));
            return body;
        });
    }

    /** messagesJson: arreglo JSON de {role, content}. Devuelve {"content": "<json del modelo>"}. */
    public static CompletableFuture<String> chat(String messagesJson) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("model", CHAT_MODEL);
            payload.add("messages", JsonParser.parseString(messagesJson).getAsJsonArray());
            payload.addProperty("temperature", 0.8);
            payload.addProperty("max_tokens", 220);
            JsonObject fmt = new JsonObject();
            fmt.addProperty("type", "json_object");
            payload.add("response_format", fmt);
            HttpRequest r = req("/chat/completions", Duration.ofSeconds(25))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8)).build();
            return send(r).thenApply(body -> {
                String content = JsonParser.parseString(body).getAsJsonObject()
                        .getAsJsonArray("choices").get(0).getAsJsonObject()
                        .getAsJsonObject("message").get("content").getAsString();
                JsonObject out = new JsonObject();
                out.addProperty("content", content);
                return out.toString();
            });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /** Voz a texto (Whisper en Groq). Devuelve {"text": "..."}. */
    public static CompletableFuture<String> stt(byte[] wav, String lang) {
        try {
            if (wav == null || wav.length < 1000) return CompletableFuture.completedFuture("{\"text\":\"\"}");
            String boundary = "----maelity" + UUID.randomUUID().toString().replace("-", "");
            ByteArrayOutputStream o = new ByteArrayOutputStream(wav.length + 1024);
            String prompt = "es".equals(lang)
                    ? "Conversación en español con Maelity en Minecraft: diamante, aldea, hierro, ayúdame, sígueme."
                    : "Conversation in English with Maelity in Minecraft: diamond, village, iron, help me, follow me.";
            field(o, boundary, "model", STT_MODEL);
            field(o, boundary, "language", "es".equals(lang) ? "es" : "en");
            field(o, boundary, "temperature", "0");
            field(o, boundary, "response_format", "json");
            field(o, boundary, "prompt", prompt);
            o.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n"
                    + "Content-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            o.write(wav);
            o.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpRequest r = req("/audio/transcriptions", Duration.ofSeconds(25))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(o.toByteArray())).build();
            return send(r).thenApply(body -> {
                String text = "";
                JsonObject j = JsonParser.parseString(body).getAsJsonObject();
                if (j.has("text") && !j.get("text").isJsonNull()) text = j.get("text").getAsString().trim();
                for (Pattern p : HALLUCINATIONS) if (p.matcher(text).find()) { text = ""; break; }
                JsonObject out = new JsonObject();
                out.addProperty("text", text);
                return out.toString();
            });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static void field(ByteArrayOutputStream o, String boundary, String name, String value) throws IOException {
        o.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    public static boolean isNoKey(Throwable t) {
        while (t != null) {
            if (t instanceof NoKeyException) return true;
            t = t.getCause();
        }
        return false;
    }

    public static boolean isBadKey(Throwable t) {
        while (t != null) {
            if (t instanceof HttpStatusException h && (h.status == 401 || h.status == 403)) return true;
            t = t.getCause();
        }
        return false;
    }

    private DirectAi() {}
}
