package net.backrooms2k10.item;

import net.backrooms2k10.Backrooms2K10;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * ModCreativeTabs:
 * Pestaña creativa personalizada para los bloques y elementos de Backrooms 2K10.
 */
public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Backrooms2K10.MOD_ID);

    public static final RegistryObject<CreativeModeTab> BACKROOMS_TAB = CREATIVE_MODE_TABS.register(
            "backrooms_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.backrooms2k10"))
                    .icon(() -> new ItemStack(ModItems.YELLOW_WALLPAPER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.YELLOW_WALLPAPER.get());
                        output.accept(ModItems.BACKROOMS_CARPET.get());
                        output.accept(ModItems.OFFICE_CEILING.get());
                        output.accept(ModItems.FLUORESCENT_LAMP.get());
                    })
                    .build()
    );
}

