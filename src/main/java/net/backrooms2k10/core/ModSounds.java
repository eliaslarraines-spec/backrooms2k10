package net.backrooms2k10.core;

import net.backrooms2k10.Backrooms2K10;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * ModSounds:
 * Registro central de eventos de sonido del mod.
 */
public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Backrooms2K10.MOD_ID);

    public static final RegistryObject<SoundEvent> FLUORESCENT_BUZZ = SOUNDS.register(
            "fluorescent_buzz",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Backrooms2K10.MOD_ID, "fluorescent_buzz"))
    );
}
