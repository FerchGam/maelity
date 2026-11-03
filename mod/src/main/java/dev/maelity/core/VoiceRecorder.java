package dev.maelity.core;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;

/**
 * Graba del microfono PREDETERMINADO del sistema (AudioSystem.getTargetDataLine usa el mixer por
 * defecto; en Windows corresponde al dispositivo de grabacion predeterminado). No hay selector.
 */
public final class VoiceRecorder {
    public static final int MAX_SECONDS = 20;
    private static final float[] RATES = {16000f, 44100f, 48000f, 22050f};

    private TargetDataLine line;
    private AudioFormat format;
    private Thread thread;
    private volatile boolean running;
    private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();

    public synchronized boolean isRecording() { return running; }

    /** @return null si todo fue bien, o un mensaje de error. */
    public synchronized String start() {
        if (running) return null;
        pcm.reset();
        for (float rate : RATES) {
            AudioFormat f = new AudioFormat(rate, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, f);
            if (!AudioSystem.isLineSupported(info)) continue;
            try {
                TargetDataLine l = (TargetDataLine) AudioSystem.getLine(info);
                l.open(f);
                l.start();
                line = l;
                format = f;
                running = true;
                final int maxBytes = (int) (rate * 2 * MAX_SECONDS);
                thread = new Thread(() -> {
                    byte[] buf = new byte[4096];
                    while (running) {
                        int n = l.read(buf, 0, buf.length);
                        if (n <= 0) break;
                        synchronized (pcm) {
                            pcm.write(buf, 0, n);
                            if (pcm.size() >= maxBytes) running = false;
                        }
                    }
                }, "Maelity-Mic");
                thread.setDaemon(true);
                thread.start();
                return null;
            } catch (Exception ex) {
                // probar el siguiente formato
            }
        }
        return "No microphone available";
    }

    /** Detiene y devuelve un WAV listo para enviar, o null si fue demasiado corto o silencioso. */
    public synchronized byte[] stopAsWav() {
        if (line == null) return null;
        running = false;
        try { if (thread != null) thread.join(300); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        try { line.stop(); line.close(); } catch (Exception ignored) {}
        line = null;
        byte[] data;
        synchronized (pcm) { data = pcm.toByteArray(); }
        float rate = format.getSampleRate();
        if (data.length < rate * 2 * 0.35) return null;      // < 0.35 s
        if (rms(data) < 120) return null;                    // silencio
        return toWav(data, (int) rate);
    }

    public synchronized void cancel() {
        if (line == null) return;
        running = false;
        try { line.stop(); line.close(); } catch (Exception ignored) {}
        line = null;
    }

    public static double rms(byte[] pcm16le) {
        long sum = 0;
        int n = pcm16le.length / 2;
        if (n == 0) return 0;
        for (int i = 0; i < n; i++) {
            int s = (short) ((pcm16le[2 * i] & 0xff) | (pcm16le[2 * i + 1] << 8));
            sum += (long) s * s;
        }
        return Math.sqrt(sum / (double) n);
    }

    public static byte[] toWav(byte[] pcm16le, int sampleRate) {
        int dataLen = pcm16le.length;
        byte[] out = new byte[44 + dataLen];
        writeStr(out, 0, "RIFF");
        writeInt(out, 4, 36 + dataLen);
        writeStr(out, 8, "WAVE");
        writeStr(out, 12, "fmt ");
        writeInt(out, 16, 16);
        writeShort(out, 20, 1);            // PCM
        writeShort(out, 22, 1);            // mono
        writeInt(out, 24, sampleRate);
        writeInt(out, 28, sampleRate * 2); // byte rate
        writeShort(out, 32, 2);            // block align
        writeShort(out, 34, 16);           // bits
        writeStr(out, 36, "data");
        writeInt(out, 40, dataLen);
        System.arraycopy(pcm16le, 0, out, 44, dataLen);
        return out;
    }

    private static void writeStr(byte[] b, int o, String s) { for (int i = 0; i < s.length(); i++) b[o + i] = (byte) s.charAt(i); }
    private static void writeInt(byte[] b, int o, int v) { b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); b[o + 2] = (byte) (v >> 16); b[o + 3] = (byte) (v >> 24); }
    private static void writeShort(byte[] b, int o, int v) { b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); }
}
