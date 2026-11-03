package dev.maelity;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MaelityMod.MODID);

    public static final DeferredItem<DeferredSpawnEggItem> MAELITY_SPAWN_EGG = ITEMS.register("maelity_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.MAELITY, 0xFFD60A, 0x2B2B2B, new Item.Properties()));

    static void onCreativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.SPAWN_EGGS) e.accept(MAELITY_SPAWN_EGG);
    }

    private ModItems() {}
}
