package com.andrewbristowx.cobblepastureoptimizer.config;

import com.andrewbristowx.cobblepastureoptimizer.CobblePastureOptimizer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class OptimizerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("cobblepastureoptimizer.json");

    private static OptimizerConfig INSTANCE = new OptimizerConfig();

    public boolean floatingDisplay = true;
    public int displayRangeBlocks = 16;
    public double displayHeight = 2.35D;
    public int displayUpdateIntervalTicks = 40;
    public int maxSpeciesLines = 5;
    public int lineWidth = 220;
    public int backgroundArgb = 0x60000000;

    // Display appearance. Alpha.4 defaults to a fixed, clean sign-like look.
    public boolean facePlayer = false;
    public boolean textShadow = false;
    public boolean showBackground = false;

    public boolean showPokemonCount = true;
    public boolean showLevels = false;
    public boolean showHopperStatus = true;
    public boolean showEggCount = true;
    public boolean showBreedingStatus = true;

    public static OptimizerConfig get() {
        return INSTANCE;
    }

    public static void load() {
        OptimizerConfig loaded = new OptimizerConfig();

        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                OptimizerConfig parsed = GSON.fromJson(reader, OptimizerConfig.class);
                if (parsed != null) {
                    loaded = parsed;
                }
            } catch (Exception exception) {
                CobblePastureOptimizer.LOGGER.error(
                        "Could not read {}. Using safe defaults.",
                        CONFIG_PATH,
                        exception
                );
            }
        }

        loaded.normalize();
        INSTANCE = loaded;
        save();
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException exception) {
            CobblePastureOptimizer.LOGGER.error("Could not write {}", CONFIG_PATH, exception);
        }
    }

    private void normalize() {
        displayRangeBlocks = Math.max(4, Math.min(displayRangeBlocks, 64));
        displayHeight = Math.max(1.25D, Math.min(displayHeight, 6.0D));
        displayUpdateIntervalTicks = Math.max(20, Math.min(displayUpdateIntervalTicks, 400));
        maxSpeciesLines = Math.max(1, Math.min(maxSpeciesLines, 12));
        lineWidth = Math.max(80, Math.min(lineWidth, 400));
    }
}
