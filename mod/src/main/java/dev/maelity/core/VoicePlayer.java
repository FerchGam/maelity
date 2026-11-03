package dev.maelity.core;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Reproduce WAV en un hilo propio; una voz nueva corta la anterior. */
public final class VoicePlayer {
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Maelity-Voice");
        t.setDaemon(true);
        return t;
    });
    private static volatile Clip current;

    public static void play(byte[] wav, float volume01) {
        stop();
        EXEC.submit(() -> {
            try (AudioInputStream in = open(wav)) {
                Clip clip = AudioSystem.getClip();
                CountDownLatch done = new CountDownLatch(1);
                clip.addLineListener(ev -> { if (ev.getType() == LineEvent.Type.STOP) done.countDown(); });
                clip.open(in);
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl g = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    float v = Math.max(0.0001f, Math.min(1f, volume01));
                    float db = (float) (20.0 * Math.log10(v));
                    g.setValue(Math.max(g.getMinimum(), Math.min(g.getMaximum(), db)));
                }
                current = clip;
                clip.start();
                done.await(60, TimeUnit.SECONDS);
                clip.close();
            } catch (Exception ignored) {
                // sin audio no pasa nada: el texto ya salio en el chat
            }
        });
    }

    /**
     * Abre un WAV. Java Sound rechaza los WAV con cabecera "de streaming" (tamanos 0 o 0xFFFFFFFF), que algunos
     * proveedores de TTS devuelven; en ese caso se lee el PCM a mano para que la voz no falle en silencio.
     */
    public static AudioInputStream open(byte[] wav) throws Exception {
        try {
            AudioInputStream s = AudioSystem.getAudioInputStream(new BufferedInputStream(new ByteArrayInputStream(wav)));
            long bytes = s.getFrameLength() * Math.max(1, s.getFormat().getFrameSize());
            if (s.getFrameLength() > 0 && bytes <= wav.length) return s;   // longitud creible
            s.close();                                                     // longitud falsa (streaming): lector manual
        } catch (UnsupportedAudioFileException | java.io.IOException ignored) {
            // cae al lector manual
        }
        if (wav.length < 44 || wav[0] != 'R' || wav[1] != 'I' || wav[2] != 'F' || wav[3] != 'F') {
            throw new UnsupportedAudioFileException("not a WAV");
        }
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int pos = 12;
        int fmt = -1, ch = 0, rate = 0, bits = 0;
        while (pos + 8 <= wav.length) {
            String id = new String(wav, pos, 4, StandardCharsets.US_ASCII);
            long size = b.getInt(pos + 4) & 0xFFFFFFFFL;
            int body = pos + 8;
            if (id.equals("fmt ")) {
                fmt = b.getShort(body) & 0xFFFF;
                ch = b.getShort(body + 2) & 0xFFFF;
                rate = b.getInt(body + 4);
                bits = b.getShort(body + 14) & 0xFFFF;
            } else if (id.equals("data")) {
                if ((fmt != 1 && fmt != 0xFFFE) || bits != 16 || ch < 1) {
                    throw new UnsupportedAudioFileException("only 16-bit PCM WAV is supported");
                }
                int avail = wav.length - body;
                int len = (size == 0 || size > avail) ? avail : (int) size;
                int frame = ch * 2;
                len -= len % frame;
                AudioFormat f = new AudioFormat(rate, 16, ch, true, false);
                return new AudioInputStream(new ByteArrayInputStream(wav, body, len), f, len / frame);
            }
            long next = body + size + (size & 1);
            if (next > wav.length) break;
            pos = (int) next;
        }
        throw new UnsupportedAudioFileException("WAV without data chunk");
    }

    public static void stop() {
        Clip c = current;
        if (c != null) {
            try { c.stop(); c.close(); } catch (Exception ignored) {}
        }
    }

    private VoicePlayer() {}
}
