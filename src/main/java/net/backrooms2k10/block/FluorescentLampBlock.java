package net.backrooms2k10.block;

import net.backrooms2k10.client.FluorescentLampSoundHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * FluorescentLampBlock:
 * Lámpara fluorescente típica de oficina. Emite luz (nivel 15) y produce
 * sonido de zumbido e interferencia eléctrica ambiental mediante animateTick client-side,
 * optimizado para no sobrecargar el audio ni la CPU al existir múltiples fuentes.
 */
public class FluorescentLampBlock extends Block {

    public FluorescentLampBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.isClientSide) {
            FluorescentLampSoundHandler.tryPlayBuzz(level, pos, random);
        }
    }
}

