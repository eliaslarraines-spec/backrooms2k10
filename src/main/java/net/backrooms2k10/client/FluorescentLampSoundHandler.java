package net.backrooms2k10.client;

import net.backrooms2k10.core.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * FluorescentLampSoundHandler:
 * Manejador eficiente de sonido ambiental para lámparas fluorescentes.
 * Evita miles de fuentes de sonido simultáneas y no genera BlockEntities
 * ni tareas periódicas pesadas.
 * Controla un intervalo mínimo de enfriamiento (cooldown) y probabilidad
 * para mantener un sonido ambiental sutil, orgánico y de alto rendimiento.
 */
public class FluorescentLampSoundHandler {
    private static long lastSoundPlayedMillis = 0L;
    // Intervalo mínimo entre sonidos en milisegundos (evita saturación y acumulación acústica)
    private static final long MIN_INTERVAL_MILLIS = 3000L;

    public static void tryPlayBuzz(Level level, BlockPos pos, RandomSource random) {
        // Probabilidad baja en cada tick animado para evitar sobrecarga (1 en 100)
        if (random.nextInt(100) != 0) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastSoundPlayedMillis < MIN_INTERVAL_MILLIS) {
            return;
        }

        lastSoundPlayedMillis = now;
        // Variación sutil de pitch para una atmósfera natural y desconcertante
        float pitch = 0.92f + random.nextFloat() * 0.16f;
        level.playLocalSound(
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                ModSounds.FLUORESCENT_BUZZ.get(),
                SoundSource.BLOCKS,
                0.35f,
                pitch,
                false
        );
    }
}

