package net.backrooms2k10.core;

import net.backrooms2k10.Backrooms2K10;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Backrooms2K10.MOD_ID);

    public static final RegistryObject<SoundEvent> PORTAL_ACTIVATE = SOUNDS.register(
            "portal_activate",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Backrooms2K10.MOD_ID, "portal_activate"))
    );

    public static final RegistryObject<SoundEvent> PORTAL_DEACTIVATE = SOUNDS.register(
            "portal_deactivate",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Backrooms2K10.MOD_ID, "portal_deactivate"))
    );

    public static final RegistryObject<SoundEvent> PORTAL_TRAVEL = SOUNDS.register(
            "portal_travel",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Backrooms2K10.MOD_ID, "portal_travel"))
    );

    public static final RegistryObject<SoundEvent> PORTAL_ERROR = SOUNDS.register(
            "portal_error",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Backrooms2K10.MOD_ID, "portal_error"))
    );
}
