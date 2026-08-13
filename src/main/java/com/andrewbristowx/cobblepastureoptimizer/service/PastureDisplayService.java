package com.andrewbristowx.cobblepastureoptimizer.service;

import com.andrewbristowx.cobblepastureoptimizer.CobblePastureOptimizer;
import com.andrewbristowx.cobblepastureoptimizer.config.OptimizerConfig;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maintains at most one vanilla TextDisplay per non-empty Pasture.
 *
 * The display is intentionally vanilla/server-side: clients do not need this mod to render it.
 * TextDisplay has no AI/pathfinding/collisions, and its NBT is only rewritten when the rendered
 * contents actually change.
 */
public final class PastureDisplayService {
    private static final String DISPLAY_TAG = "cpo_pasture_display";
    private static final String POSITION_TAG_PREFIX = "cpo_pasture_pos:";

    private static final Map<String, String> LAST_RENDERED_TEXT = new HashMap<>();
    private static long cleanupTicker;

    private PastureDisplayService() {
    }

    public static void tick(
            ServerLevel world,
            BlockPos pasturePos,
            PokemonPastureBlockEntity pasture,
            List<PokemonPastureBlockEntity.Tethering> tetherings
    ) {
        OptimizerConfig config = OptimizerConfig.get();

        if (!config.floatingDisplay) {
            removeDisplaysNear(world, pasturePos);
            return;
        }

        // Stagger refreshes by block position so hundreds of Pastures do not all refresh on one tick.
        int interval = config.displayUpdateIntervalTicks;
        long phase = Math.floorMod(pasturePos.asLong(), interval);
        if (Math.floorMod(world.getGameTime(), interval) != phase) {
            return;
        }

        List<Pokemon> pokemon = new ArrayList<>();
        for (PokemonPastureBlockEntity.Tethering tethering : tetherings) {
            Pokemon candidate = tethering.getPokemon();
            if (candidate == null) {
                continue;
            }
            if (candidate.getTetheringId() == null || !candidate.getTetheringId().equals(tethering.getTetheringId())) {
                continue;
            }
            pokemon.add(candidate);
        }

        if (pokemon.isEmpty()) {
            removeDisplaysNear(world, pasturePos);
            LAST_RENDERED_TEXT.remove(cacheKey(world, pasturePos));
            return;
        }

        MutableComponent text = buildText(world, pasturePos, pasture, pokemon, config);
        String serializedText = Component.Serializer.toJson(text, world.registryAccess());
        if (serializedText == null) {
            return;
        }

        Display.TextDisplay display = findOrCreateDisplay(world, pasturePos, config);
        if (display == null) {
            return;
        }

        String key = cacheKey(world, pasturePos);
        String previous = LAST_RENDERED_TEXT.get(key);
        if (!serializedText.equals(previous)) {
            applyDisplayNbt(display, serializedText, config);
            LAST_RENDERED_TEXT.put(key, serializedText);
        }

        // Keep the display exactly above the block if the configured height changed.
        double targetX = pasturePos.getX() + 0.5D;
        double targetY = pasturePos.getY() + config.displayHeight;
        double targetZ = pasturePos.getZ() + 0.5D;
        if (display.distanceToSqr(targetX, targetY, targetZ) > 0.0001D) {
            display.setPos(targetX, targetY, targetZ);
        }
    }

    /**
     * Slow safety cleanup for displays whose Pasture was broken while its normal ticker was gone.
     */
    public static void serverTick(MinecraftServer server) {
        cleanupTicker++;
        if (cleanupTicker % 100L != 0L) {
            return;
        }

        for (ServerLevel level : server.getAllLevels()) {
            List<Display.TextDisplay> stale = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Display.TextDisplay display) || !display.getTags().contains(DISPLAY_TAG)) {
                    continue;
                }

                BlockPos pasturePos = getTaggedPasturePosition(display);
                if (pasturePos == null) {
                    stale.add(display);
                    continue;
                }

                BlockEntity blockEntity = level.getBlockEntity(pasturePos);
                if (!(blockEntity instanceof PokemonPastureBlockEntity)) {
                    stale.add(display);
                    LAST_RENDERED_TEXT.remove(cacheKey(level, pasturePos));
                }
            }

            for (Display.TextDisplay display : stale) {
                display.discard();
            }
        }
    }

    private static MutableComponent buildText(
            ServerLevel world,
            BlockPos pasturePos,
            PokemonPastureBlockEntity pasture,
            List<Pokemon> pokemon,
            OptimizerConfig config
    ) {
        MutableComponent root = Component.empty();
        appendLine(root, Component.literal("✦ PASTURE ✦").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));

        if (config.showPokemonCount) {
            appendLine(
                    root,
                    Component.literal(Integer.toString(pokemon.size()))
                            .withStyle(ChatFormatting.WHITE)
                            .append(Component.literal(" Pokémon").withStyle(ChatFormatting.GRAY))
            );
        }

        List<SpeciesSummary> summaries = summarizeSpecies(pokemon);
        int shown = Math.min(config.maxSpeciesLines, summaries.size());
        for (int i = 0; i < shown; i++) {
            SpeciesSummary summary = summaries.get(i);
            MutableComponent line = summary.name().copy().withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(" ×" + summary.count()).withStyle(ChatFormatting.YELLOW));

            if (config.showLevels) {
                String levelText = summary.minLevel() == summary.maxLevel()
                        ? "  Nv." + summary.minLevel()
                        : "  Nv." + summary.minLevel() + "–" + summary.maxLevel();
                line.append(Component.literal(levelText).withStyle(ChatFormatting.DARK_GRAY));
            }
            appendLine(root, line);
        }

        if (summaries.size() > shown) {
            appendLine(
                    root,
                    Component.literal("+" + (summaries.size() - shown) + " especies")
                            .withStyle(ChatFormatting.DARK_GRAY)
            );
        }

        if (config.showHopperStatus) {
            boolean hopper = world.getBlockEntity(pasturePos.below()) instanceof HopperBlockEntity;
            MutableComponent line = Component.literal("Salida: ").withStyle(ChatFormatting.GRAY);
            if (hopper) {
                line.append(Component.literal("Tolva ✓").withStyle(ChatFormatting.GREEN));
            } else {
                line.append(Component.literal("suelo").withStyle(ChatFormatting.DARK_GRAY));
            }
            appendLine(root, line);
        }

        CobbreedingStatus breedingStatus = CobbreedingBridge.read(world, pasturePos, pasture);
        if (breedingStatus.available()) {
            if (config.showBreedingStatus) {
                MutableComponent breedingLine = Component.literal("Crianza: ").withStyle(ChatFormatting.GRAY);
                if (Boolean.FALSE.equals(breedingStatus.enabled())) {
                    breedingLine.append(Component.literal("pausada").withStyle(ChatFormatting.DARK_GRAY));
                } else if (breedingStatus.time() != null
                        && breedingStatus.time() >= 0L
                        && breedingStatus.requiredTicks() != null
                        && breedingStatus.requiredTicks() > 0) {
                    long elapsed = Math.max(0L, world.getGameTime() - breedingStatus.time());
                    int progress = (int) Math.min(100L, elapsed * 100L / breedingStatus.requiredTicks());
                    breedingLine.append(Component.literal(progress + "%").withStyle(ChatFormatting.LIGHT_PURPLE));
                } else if (Boolean.TRUE.equals(breedingStatus.enabled())) {
                    breedingLine.append(Component.literal("esperando pareja").withStyle(ChatFormatting.YELLOW));
                } else {
                    breedingLine.append(Component.literal("disponible").withStyle(ChatFormatting.GRAY));
                }
                appendLine(root, breedingLine);
            }

            if (config.showEggCount) {
                MutableComponent eggLine = Component.literal("Huevos: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(Integer.toString(breedingStatus.eggs())).withStyle(
                                breedingStatus.eggs() > 0 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GRAY
                        ));
                appendLine(root, eggLine);
            }
        }

        return root;
    }

    private static List<SpeciesSummary> summarizeSpecies(List<Pokemon> pokemon) {
        Map<ResourceLocation, SpeciesSummaryBuilder> grouped = new HashMap<>();

        for (Pokemon entry : pokemon) {
            ResourceLocation id = entry.getSpecies().getResourceIdentifier();
            SpeciesSummaryBuilder summary = grouped.computeIfAbsent(
                    id,
                    ignored -> new SpeciesSummaryBuilder(entry.getSpecies().getTranslatedName())
            );
            summary.add(entry.getLevel());
        }

        return grouped.entrySet().stream()
                .map(entry -> entry.getValue().build(entry.getKey()))
                .sorted(Comparator
                        .comparingInt(SpeciesSummary::count).reversed()
                        .thenComparing(summary -> summary.id().toString()))
                .toList();
    }

    private static Display.TextDisplay findOrCreateDisplay(
            ServerLevel world,
            BlockPos pasturePos,
            OptimizerConfig config
    ) {
        AABB searchBox = new AABB(pasturePos).inflate(2.0D, 6.0D, 2.0D);
        String positionTag = positionTag(pasturePos);
        List<Display.TextDisplay> matches = world.getEntitiesOfClass(
                Display.TextDisplay.class,
                searchBox,
                entity -> entity.getTags().contains(DISPLAY_TAG) && entity.getTags().contains(positionTag)
        );

        if (!matches.isEmpty()) {
            Display.TextDisplay primary = matches.getFirst();
            for (int i = 1; i < matches.size(); i++) {
                matches.get(i).discard();
            }
            return primary;
        }

        Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, world);
        display.setPos(
                pasturePos.getX() + 0.5D,
                pasturePos.getY() + config.displayHeight,
                pasturePos.getZ() + 0.5D
        );
        display.addTag(DISPLAY_TAG);
        display.addTag(positionTag);

        applyDisplayNbt(display, Component.Serializer.toJson(Component.literal("Pasture"), world.registryAccess()), config);

        if (!world.addFreshEntity(display)) {
            return null;
        }
        return display;
    }

    private static void applyDisplayNbt(
            Display.TextDisplay display,
            String serializedText,
            OptimizerConfig config
    ) {
        if (serializedText == null) {
            return;
        }

        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        tag.putString("text", serializedText);
        tag.putString("billboard", "center");
        tag.putFloat("view_range", config.displayRangeBlocks / 64.0F);
        tag.putInt("line_width", config.lineWidth);
        tag.putInt("background", config.backgroundArgb);
        tag.putByte("text_opacity", (byte) -1);
        tag.putBoolean("shadow", true);
        tag.putBoolean("see_through", false);
        tag.putBoolean("default_background", false);
        tag.putString("alignment", "center");
        tag.putBoolean("Invulnerable", true);
        tag.putBoolean("NoGravity", true);
        tag.putBoolean("Silent", true);
        display.load(tag);
    }

    private static void removeDisplaysNear(ServerLevel world, BlockPos pasturePos) {
        AABB searchBox = new AABB(pasturePos).inflate(2.0D, 6.0D, 2.0D);
        String positionTag = positionTag(pasturePos);
        for (Display.TextDisplay display : world.getEntitiesOfClass(Display.TextDisplay.class, searchBox)) {
            if (display.getTags().contains(DISPLAY_TAG) && display.getTags().contains(positionTag)) {
                display.discard();
            }
        }
    }

    private static BlockPos getTaggedPasturePosition(Entity display) {
        for (String tag : display.getTags()) {
            if (!tag.startsWith(POSITION_TAG_PREFIX)) {
                continue;
            }
            String[] parts = tag.substring(POSITION_TAG_PREFIX.length()).split(",", -1);
            if (parts.length != 3) {
                return null;
            }
            try {
                return new BlockPos(
                        Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2])
                );
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String positionTag(BlockPos pos) {
        return POSITION_TAG_PREFIX + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String cacheKey(ServerLevel world, BlockPos pos) {
        return world.dimension().location() + ":" + pos.asLong();
    }

    private static void appendLine(MutableComponent root, Component line) {
        if (!root.getString().isEmpty()) {
            root.append(Component.literal("\n"));
        }
        root.append(line);
    }

    private record SpeciesSummary(
            ResourceLocation id,
            MutableComponent name,
            int count,
            int minLevel,
            int maxLevel
    ) {
    }

    private static final class SpeciesSummaryBuilder {
        private final MutableComponent name;
        private int count;
        private int minLevel = Integer.MAX_VALUE;
        private int maxLevel = Integer.MIN_VALUE;

        private SpeciesSummaryBuilder(MutableComponent name) {
            this.name = name;
        }

        private void add(int level) {
            count++;
            minLevel = Math.min(minLevel, level);
            maxLevel = Math.max(maxLevel, level);
        }

        private SpeciesSummary build(ResourceLocation id) {
            return new SpeciesSummary(id, name, count, minLevel, maxLevel);
        }
    }

    private record CobbreedingStatus(
            boolean available,
            Boolean enabled,
            int eggs,
            Long time,
            Integer requiredTicks
    ) {
        private static CobbreedingStatus unavailable() {
            return new CobbreedingStatus(false, null, 0, null, null);
        }
    }

    /**
     * Optional reflection bridge so Cobbreeding remains an optional dependency.
     */
    private static final class CobbreedingBridge {
        private static boolean initialized;
        private static boolean available;
        private static boolean failureLogged;

        private static Class<?> pastureInventoryClass;
        private static Method getItemsMethod;
        private static Field breedingActivatedPropertyField;
        private static Field registryField;
        private static Method getTimeMethod;
        private static Method getRequiredTicksMethod;

        private CobbreedingBridge() {
        }

        private static CobbreedingStatus read(
                ServerLevel world,
                BlockPos pasturePos,
                PokemonPastureBlockEntity pasture
        ) {
            initialize();
            if (!available) {
                return CobbreedingStatus.unavailable();
            }

            try {
                int eggCount = 0;
                if (pastureInventoryClass.isInstance(pasture)) {
                    Object items = getItemsMethod.invoke(pasture);
                    if (items instanceof Iterable<?> iterable) {
                        for (Object value : iterable) {
                            if (value instanceof ItemStack stack && !stack.isEmpty()) {
                                eggCount += stack.getCount();
                            }
                        }
                    }
                }

                Boolean enabled = null;
                Object propertyObject = breedingActivatedPropertyField.get(null);
                if (propertyObject instanceof Property<?> property) {
                    enabled = readBooleanProperty(pasture, property);
                }

                Long time = null;
                Integer requiredTicks = null;
                Object registryObject = registryField.get(null);
                if (registryObject instanceof Map<?, ?> registry) {
                    Object data = registry.get(pasturePos);
                    if (data != null) {
                        time = ((Number) getTimeMethod.invoke(data)).longValue();
                        requiredTicks = ((Number) getRequiredTicksMethod.invoke(data)).intValue();
                    }
                }

                return new CobbreedingStatus(true, enabled, eggCount, time, requiredTicks);
            } catch (Throwable throwable) {
                logFailureOnce("Could not read Cobbreeding pasture status; hiding breeding fields.", throwable);
                return CobbreedingStatus.unavailable();
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static Boolean readBooleanProperty(PokemonPastureBlockEntity pasture, Property<?> property) {
            try {
                Comparable value = pasture.getBlockState().getValue((Property) property);
                return value instanceof Boolean bool ? bool : null;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static void initialize() {
            if (initialized) {
                return;
            }
            initialized = true;

            if (!FabricLoader.getInstance().isModLoaded("cobbreeding")) {
                return;
            }

            try {
                pastureInventoryClass = Class.forName("ludichat.cobbreeding.PastureInventory");
                getItemsMethod = pastureInventoryClass.getMethod("getItems");

                Class<?> dataClass = Class.forName("ludichat.cobbreeding.PastureBreedingData");
                registryField = dataClass.getField("registry");
                getTimeMethod = dataClass.getMethod("getTime");
                getRequiredTicksMethod = dataClass.getMethod("getRequiredTicks");

                Class<?> pastureMixinClass = Class.forName(
                        "ludichat.cobbreeding.mixin.PokemonPastureBlockEntityMixin"
                );
                breedingActivatedPropertyField = pastureMixinClass.getDeclaredField(
                        "cobbreeding$BREEDING_ACTIVATED"
                );
                breedingActivatedPropertyField.setAccessible(true);

                available = true;
            } catch (Throwable throwable) {
                available = false;
                logFailureOnce("Cobbreeding is installed but its 2.2.2 pasture API could not be linked.", throwable);
            }
        }

        private static void logFailureOnce(String message, Throwable throwable) {
            if (failureLogged) {
                return;
            }
            failureLogged = true;
            CobblePastureOptimizer.LOGGER.warn(message, throwable);
        }
    }
}
