package net.backrooms2k10;

import net.backrooms2k10.block.ModBlocks;
import net.backrooms2k10.core.ModConfig;
import net.backrooms2k10.core.ModSounds;
import net.backrooms2k10.item.ModCreativeTabs;
import net.backrooms2k10.item.ModItems;
import net.backrooms2k10.network.ModNetworking;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Backrooms2K10.MOD_ID)
public class Backrooms2K10 {
    public static final String MOD_ID = "backrooms2k10";
    public static final Logger LOGGER = LoggerFactory.getLogger("Backrooms2K10");

    public Backrooms2K10() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Registrar sonidos
        ModSounds.SOUNDS.register(modEventBus);

        // Registrar bloques e items
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);

        // Registrar pestaña creativa
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        // Registrar configuración
        ModLoadingContext.get().registerConfig(Type.SERVER, ModConfig.SPEC);

        // Ciclo de vida
        modEventBus.addListener(this::commonSetup);

        // Eventos forge
        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("[Backrooms] Mod inicializado con arquitectura limpia y modular.");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModNetworking.register();
            LOGGER.info("[Backrooms] Networking inicializado.");
        });
    }
}
