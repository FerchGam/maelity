package dev.maelity.net;

import dev.maelity.MaelityMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: lo que Maelity dice (texto + personalidad para el estilo de voz). */
public record SayPayload(int entityId, String text, String mood, String lang) implements CustomPacketPayload {
    public static final Type<SayPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MaelityMod.MODID, "say"));

    public static final StreamCodec<ByteBuf, SayPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SayPayload::entityId,
            ByteBufCodecs.stringUtf8(400), SayPayload::text,
            ByteBufCodecs.stringUtf8(32), SayPayload::mood,
            ByteBufCodecs.stringUtf8(8), SayPayload::lang,
            SayPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
