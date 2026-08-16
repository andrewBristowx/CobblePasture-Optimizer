package com.andrewbristowx.cobblepastureoptimizer;

import com.andrewbristowx.cobblepastureoptimizer.config.OptimizerConfig;
import com.andrewbristowx.cobblepastureoptimizer.service.PastureDisplayService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CobblePastureOptimizer implements ModInitializer {
    public static final String MOD_ID = "cobblepastureoptimizer";
    public static final Logger LOGGER = LoggerFactory.getLogger("CobblePasture Optimizer");

    @Override
    public void onInitialize() {
        OptimizerConfig.load();
        // Este evento se ejecuta tanto en servidor dedicado como en el servidor integrado de singleplayer/LAN.
        ServerTickEvents.END_SERVER_TICK.register(PastureDisplayService::serverTick);
        LOGGER.info("CobblePasture Optimizer 0.1.0-alpha.7 activo: servidor dedicado, LAN y singleplayer integrado compatibles.");
    }
}
