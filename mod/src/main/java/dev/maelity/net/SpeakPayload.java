package dev.maelity.net;

import dev.maelity.MaelityMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cliente -> servidor: lo que el jugador dijo (ya transcrito) y su idioma elegido. */
public record SpeakPayload(String text, String lang) implements CustomPacketPayload {
    public static final Type<SpeakPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaelityMod.MODID, "speak"));

    public static final StreamCodec<ByteBuf, SpeakPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(400), SpeakPayload::text,
            ByteBufCodecs.stringUtf8(8), SpeakPayload::lang,
            SpeakPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
