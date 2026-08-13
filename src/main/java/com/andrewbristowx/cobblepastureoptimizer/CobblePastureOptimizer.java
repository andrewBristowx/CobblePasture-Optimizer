package com.andrewbristowx.cobblepastureoptimizer;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CobblePastureOptimizer implements ModInitializer {
    public static final String MOD_ID = "cobblepastureoptimizer";
    public static final Logger LOGGER = LoggerFactory.getLogger("CobblePasture Optimizer");

    @Override
    public void onInitialize() {
        LOGGER.info("CobblePasture Optimizer 0.1.0-alpha.1 enabled: pasture Pokemon will be virtualized server-side.");
    }
}
