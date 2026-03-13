package com.example.villagershop;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Manages the in-memory trade list and persistence to trades.yml.
 *
 * Serialization: ItemStack.serialize() produces a Map<String,Object> with a
 * "schema_version" key in Paper 1.21.11 (confirmed in ItemStack.java line 514).
 * ItemStack.deserialize(Map) checks for "schema_version" and routes to the
 * modern unsafe deserialization path.  Both are round-trip safe.
 *
 * Thread safety: all trade mutations are synchronized on this object.
 * File writes use write-then-rename (ATOMIC_MOVE) to prevent partial-write
 * corruption.  If the filesystem does not support ATOMIC_MOVE (e.g. cross-device
 * on Windows), we fall back to a plain replace, logging a warning once.
 */
public class TradeManager {

    private final VillagerShop plugin;
    private final Logger log;
    private final File tradesFile;

    /** In-memory trade list.  Each entry is a (input, optional input2, output) tuple. */
    private final List<TradePair> trades = new ArrayList<>();

    /** True once we have logged the ATOMIC_MOVE fallback warning. */
    private boolean warnedAtomicMove = false;

    public TradeManager(VillagerShop plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.tradesFile = new File(plugin.getDataFolder(), "trades.yml");
    }

    // -------------------------------------------------------------------------
    // Inner type
    // -------------------------------------------------------------------------

    /**
     * A saved trade.
     *
     * @param input   First (required) ingredient.
     * @param input2  Second (optional) ingredient.  Null means single-ingredient trade.
     * @param output  Result item.
     */
    public record TradePair(ItemStack input, @Nullable ItemStack input2, ItemStack output) {}

    // -------------------------------------------------------------------------
    // Public accessors (reads don't need synchronization since the list is only
    // mutated from the main thread after the add/remove calls)
    // -------------------------------------------------------------------------

    /**
     * Returns an unmodifiable snapshot of the current trade list.
     * Safe to call on any thread.
     */
    public synchronized List<TradePair> getTrades() {
        return Collections.unmodifiableList(new ArrayList<>(trades));
    }

    /**
     * Appends a trade and persists immediately.
     * Must be called from the main thread (or synchronized callers).
     *
     * @param input   First ingredient (required).
     * @param input2  Second ingredient, or null for a single-ingredient trade.
     * @param output  Result item.
     */
    public synchronized void addTrade(ItemStack input, @Nullable ItemStack input2, ItemStack output) {
        trades.add(new TradePair(
                input.clone(),
                input2 != null ? input2.clone() : null,
                output.clone()));
        save();
    }

    /**
     * Removes the trade at 1-based index.
     * @return true if removed, false if index out of range.
     */
    public synchronized boolean removeTrade(int oneBasedIndex) {
        if (oneBasedIndex < 1 || oneBasedIndex > trades.size()) return false;
        trades.remove(oneBasedIndex - 1);
        save();
        return true;
    }

    public synchronized int size() {
        return trades.size();
    }

    // -------------------------------------------------------------------------
    // Load
    // -------------------------------------------------------------------------

    public void load() {
        if (!tradesFile.exists()) {
            plugin.saveResource("trades.yml", false);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(tradesFile);
        List<?> rawList = yaml.getList("trades", Collections.emptyList());

        synchronized (this) {
            trades.clear();
            for (int i = 0; i < rawList.size(); i++) {
                Object entry = rawList.get(i);
                if (!(entry instanceof Map<?, ?> map)) {
                    log.warning("trades.yml entry #" + (i + 1) + " is not a map, skipping.");
                    continue;
                }

                try {
                    // Each entry has "input", optional "input2", and "output" as nested maps.
                    Object rawInput  = map.get("input");
                    Object rawOutput = map.get("output");
                    if (!(rawInput instanceof Map<?, ?> inputMap) || !(rawOutput instanceof Map<?, ?> outputMap)) {
                        log.warning("trades.yml entry #" + (i + 1) + " missing input/output map, skipping.");
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    ItemStack input = ItemStack.deserialize((Map<String, Object>) inputMap);
                    @SuppressWarnings("unchecked")
                    ItemStack output = ItemStack.deserialize((Map<String, Object>) outputMap);

                    // Guard: deserialize may return AIR if the material is unknown
                    if (input == null || input.getType().isAir()) {
                        log.warning("trades.yml entry #" + (i + 1) + " input deserialized to null/AIR, skipping.");
                        continue;
                    }
                    if (output == null || output.getType().isAir()) {
                        log.warning("trades.yml entry #" + (i + 1) + " output deserialized to null/AIR, skipping.");
                        continue;
                    }

                    // Optional second ingredient — absent key means single-ingredient trade.
                    ItemStack input2 = null;
                    Object rawInput2 = map.get("input2");
                    if (rawInput2 instanceof Map<?, ?> input2Map) {
                        @SuppressWarnings("unchecked")
                        ItemStack deserialized2 = ItemStack.deserialize((Map<String, Object>) input2Map);
                        if (deserialized2 == null || deserialized2.getType().isAir()) {
                            log.warning("trades.yml entry #" + (i + 1) + " input2 deserialized to null/AIR, treating as absent.");
                        } else {
                            input2 = deserialized2;
                        }
                    }

                    trades.add(new TradePair(input, input2, output));
                } catch (Exception e) {
                    log.warning("trades.yml entry #" + (i + 1) + " failed to deserialize: " + e.getMessage() + ", skipping.");
                }
            }
        }
        log.info("Loaded " + trades.size() + " trade(s) from trades.yml.");
    }

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------

    /**
     * Rewrites trades.yml atomically.  Must be called while holding this monitor.
     *
     * Failure modes guarded:
     * - Partial write corruption: we write to a temp file then rename.
     * - ATOMIC_MOVE unsupported: fall back to REPLACE_EXISTING with a one-time warning.
     */
    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (TradePair pair : trades) {
            Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("input",  pair.input().serialize());
            // Only write input2 when present — absent key = single-ingredient trade on load.
            if (pair.input2() != null) {
                entry.put("input2", pair.input2().serialize());
            }
            entry.put("output", pair.output().serialize());
            list.add(entry);
        }
        yaml.set("trades", list);

        File dataFolder = plugin.getDataFolder();
        dataFolder.mkdirs();

        // Write to temp file first
        File tempFile = new File(dataFolder, "trades.yml.tmp");
        try {
            yaml.save(tempFile);
        } catch (IOException e) {
            log.severe("Failed to write temp trades file: " + e.getMessage());
            return;
        }

        // Atomic rename
        try {
            Files.move(tempFile.toPath(), tradesFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            if (!warnedAtomicMove) {
                log.warning("Filesystem does not support ATOMIC_MOVE; falling back to non-atomic replace. " +
                        "This is safe in single-server use but may corrupt trades.yml on a power failure during a save.");
                warnedAtomicMove = true;
            }
            try {
                Files.move(tempFile.toPath(), tradesFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e2) {
                log.severe("Failed to move temp trades file: " + e2.getMessage());
            }
        } catch (IOException e) {
            log.severe("Failed to move temp trades file: " + e.getMessage());
        }
    }
}
