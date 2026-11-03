package dev.maelity.ai;

import dev.maelity.MaelityConfig;
import dev.maelity.core.GiftPolicy;
import dev.maelity.entity.MaelityEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/** Regalos: Maelity puede sacar cualquier objeto (como en creativo), pero GiftPolicy decide si quiere y cuanto. */
public final class Gifts {
    /** reason: ok | prank | angry | cooldown | too_valuable | banned | disabled | unknown_item */
    public record Result(boolean given, String itemName, int count, String reason) {}

    public static Result give(ServerPlayer player, MaelityEntity maelity, String rawId, int count) {
        if (!MaelityConfig.COMMON.giftsEnabled.get()) return new Result(false, rawId, 0, "disabled");
        String id = GiftPolicy.normalizeId(rawId);
        Item item = resolve(id);
        if (item == null) return new Result(false, rawId, 0, "unknown_item");

        long now = maelity.level().getGameTime();
        GiftPolicy.Tier max;
        try {
            max = GiftPolicy.Tier.valueOf(MaelityConfig.COMMON.giftsMaxTier.get().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            max = GiftPolicy.Tier.RARE;
        }
        GiftPolicy.Decision d = GiftPolicy.decide(maelity.mood().get().id, maelity.mood().anger(), maelity.mood().affection(),
                id, count, max, maelity.ownerInDanger(), maelity.giftCooldownLeft(now), maelity.getRandom().nextDouble());
        if (!d.allowed()) return new Result(false, new ItemStack(item).getHoverName().getString(), 0, d.reason());

        Item give = resolve(d.itemId());
        if (give == null) return new Result(false, rawId, 0, "unknown_item");
        ItemStack stack = new ItemStack(give, Math.min(d.count(), new ItemStack(give).getMaxStackSize()));
        String name = stack.getHoverName().getString();
        int n = stack.getCount();
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        maelity.markGift(GiftPolicy.tierOf(id), now);
        maelity.swing(InteractionHand.MAIN_HAND);
        player.playNotifySound(SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.NEUTRAL, 0.6f, 1.4f);
        maelity.recordEvent("you gave the player " + n + "x " + name + ("prank".equals(d.reason()) ? " (as a prank)" : ""));
        return new Result(true, name, n, d.reason());
    }

    private static Item resolve(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return null;
        Item it = BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
        return it == null || it == Items.AIR ? null : it;
    }

    /** Explicacion corta (en gris) cuando no regala nada. */
    public static String refusalLine(Result r, String lang) {
        boolean es = "es".equals(lang);
        return switch (r.reason()) {
            case "angry" -> es ? "§7(Maelity está demasiado enfadada para regalarte nada.)" : "§7(Maelity is too angry to give you anything.)";
            case "cooldown" -> es ? "§7(Maelity ya te regaló algo así hace poco. Espera un rato.)" : "§7(Maelity gave you something like that recently. Wait a bit.)";
            case "too_valuable" -> es ? "§7(Eso es demasiado valioso para Maelity ahora mismo. Gánate su cariño.)" : "§7(That's too valuable for Maelity right now. Earn her trust.)";
            case "banned" -> es ? "§7(Maelity no puede dar ese objeto.)" : "§7(Maelity can't give that item.)";
            case "disabled" -> es ? "§7(Los regalos de Maelity están desactivados en la configuración.)" : "§7(Maelity's gifts are disabled in the config.)";
            default -> es ? "§7(Maelity no conoce ese objeto.)" : "§7(Maelity doesn't know that item.)";
        };
    }

    private Gifts() {}
}
