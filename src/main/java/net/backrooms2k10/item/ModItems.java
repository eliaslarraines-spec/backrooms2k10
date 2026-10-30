package net.backrooms2k10.item;

import net.backrooms2k10.Backrooms2K10;
import net.backrooms2k10.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * ModItems:
 * Registro central de items asociados a los bloques.
 */
public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Backrooms2K10.MOD_ID);

    public static final RegistryObject<Item> YELLOW_WALLPAPER = ITEMS.register(
            "yellow_wallpaper",
            () -> new BlockItem(ModBlocks.YELLOW_WALLPAPER.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> BACKROOMS_CARPET = ITEMS.register(
            "backrooms_carpet",
            () -> new BlockItem(ModBlocks.BACKROOMS_CARPET.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> OFFICE_CEILING = ITEMS.register(
            "office_ceiling",
            () -> new BlockItem(ModBlocks.OFFICE_CEILING.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> FLUORESCENT_LAMP = ITEMS.register(
            "fluorescent_lamp",
            () -> new BlockItem(ModBlocks.FLUORESCENT_LAMP.get(), new Item.Properties())
    );
}

