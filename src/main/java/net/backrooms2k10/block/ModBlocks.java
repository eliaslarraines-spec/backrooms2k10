package net.backrooms2k10.block;

import net.backrooms2k10.Backrooms2K10;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * ModBlocks:
 * Registro central de bloques para Backrooms 2K10.
 */
public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Backrooms2K10.MOD_ID);

    // 1. Yellow Wallpaper - Papel mural amarillo envejecido
    public static final RegistryObject<Block> YELLOW_WALLPAPER = BLOCKS.register(
            "yellow_wallpaper",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(1.5f, 3.0f)
                    .sound(SoundType.WOOD))
    );

    // 2. Backrooms Carpet - Alfombra amarilla/beige vieja y sucia
    public static final RegistryObject<CarpetBlock> BACKROOMS_CARPET = BLOCKS.register(
            "backrooms_carpet",
            () -> new CarpetBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(0.1f)
                    .sound(SoundType.WOOL))
    );

    // 3. Office Ceiling - Techo de oficina con placas acústicas
    public static final RegistryObject<Block> OFFICE_CEILING = BLOCKS.register(
            "office_ceiling",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ)
                    .strength(1.2f, 2.5f)
                    .sound(SoundType.STONE))
    );

    // 4. Fluorescent Lamp - Lámpara fluorescente emisora de luz y zumbido
    public static final RegistryObject<FluorescentLampBlock> FLUORESCENT_LAMP = BLOCKS.register(
            "fluorescent_lamp",
            () -> new FluorescentLampBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW)
                    .lightLevel(state -> 15)
                    .strength(0.6f, 1.0f)
                    .sound(SoundType.GLASS))
    );
}

