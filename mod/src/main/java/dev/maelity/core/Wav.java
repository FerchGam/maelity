package dev.maelity.core;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Envuelve PCM de 16 bits en un WAV (ElevenLabs devuelve PCM crudo). */
public final class Wav {
    public static byte[] fromPcm16(byte[] pcm, int sampleRate, int channels) {
        int len = pcm.length - (pcm.length % (2 * channels));
        ByteBuffer b = ByteBuffer.allocate(44 + len).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + len).put(new byte[]{'W', 'A', 'V', 'E'});
        b.put(new byte[]{'f', 'm', 't', ' '}).putInt(16).putShort((short) 1).putShort((short) channels)
                .putInt(sampleRate).putInt(sampleRate * channels * 2).putShort((short) (channels * 2)).putShort((short) 16);
        b.put(new byte[]{'d', 'a', 't', 'a'}).putInt(len).put(pcm, 0, len);
        return b.array();
    }

    public static boolean isWav(byte[] b) {
        return b != null && b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F';
    }

    private Wav() {}
}
