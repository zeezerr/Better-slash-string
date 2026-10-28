package com.betterstring;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/** Timestamp-based per-player cooldowns, optionally persisted to cooldowns.yml (keyed by UUID). */
public final class CooldownManager {

    private final BetterStringPlugin plugin;
    private final File file;
    private final boolean persist;

    private final Map<UUID, Long> expiries = new ConcurrentHashMap<>();
    private final Object fileLock = new Object();
    private final AtomicBoolean saveQueued = new AtomicBoolean(false);

    public CooldownManager(BetterStringPlugin plugin, File file, boolean persist) {
        this.plugin = plugin;
        this.file = file;
        this.persist = persist;
    }

    /** Remaining cooldown in milliseconds (0 if none). Never negative. */
    public long getRemainingMillis(UUID id) {
        Long expiry = expiries.get(id);
        if (expiry == null) {
            return 0L;
        }
        long remaining = expiry - System.currentTimeMillis();
        if (remaining <= 0L) {
            expiries.remove(id, expiry);
            return 0L;
        }
        return remaining;
    }

    /** Starts a cooldown for the given player and persists it. */
    public void start(UUID id, long durationMillis) {
        if (durationMillis <= 0L) {
            return;
        }
        expiries.put(id, System.currentTimeMillis() + durationMillis);
        requestSave();
    }

    /** Removes expired entries (safe to call from any thread). */
    public void cleanup() {
        long now = System.currentTimeMillis();
        if (expiries.values().removeIf(expiry -> expiry <= now)) {
            requestSave();
        }
    }

    /** Loads cooldowns from disk, ignoring expired/malformed entries. */
    public void load(long maxDurationMillis) {
        if (!persist || !file.exists()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING,
                    "cooldowns.yml is damaged and could not be read; starting with no cooldowns.", e);
            try {
                Files.move(file.toPath(), file.toPath().resolveSibling("cooldowns.yml.corrupt"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // best effort only
            }
            return;
        }

        ConfigurationSection section = yaml.getConfigurationSection("cooldowns");
        if (section == null) {
            return;
        }
        long now = System.currentTimeMillis();
        int loaded = 0;
        int skipped = 0;
        for (String key : section.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                skipped++;
                continue;
            }
            Object raw = section.get(key);
            if (!(raw instanceof Number number)) {
                skipped++;
                continue;
            }
            long expiry = number.longValue();
            if (expiry <= now) {
                continue; // expired
            }
            // Never keep a cooldown longer than the configured duration (clock changes / lowered config).
            expiries.put(id, Math.min(expiry, now + maxDurationMillis));
            loaded++;
        }
        if (skipped > 0) {
            plugin.getLogger().warning("Ignored " + skipped + " malformed entries in cooldowns.yml.");
        }
        if (loaded > 0) {
            plugin.getLogger().info("Loaded " + loaded + " active cooldown(s).");
        }
    }

    /** Queues a coalesced asynchronous save. */
    private void requestSave() {
        if (!persist || !plugin.isEnabled()) {
            return;
        }
        if (!saveQueued.compareAndSet(false, true)) {
            return; // a save is already pending and will include this change
        }
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                saveQueued.set(false);
                writeFile();
            });
        } catch (RuntimeException e) {
            saveQueued.set(false);
            plugin.getLogger().log(Level.WARNING, "Could not schedule cooldown save.", e);
        }
    }

    /** Synchronous save (used on shutdown). */
    public void saveNow() {
        if (persist) {
            writeFile();
        }
    }

    private void writeFile() {
        synchronized (fileLock) {
            long now = System.currentTimeMillis();
            YamlConfiguration yaml = new YamlConfiguration();
            for (Map.Entry<UUID, Long> entry : expiries.entrySet()) {
                if (entry.getValue() > now) {
                    yaml.set("cooldowns." + entry.getKey(), entry.getValue());
                }
            }
            Path target = file.toPath();
            Path temp = target.resolveSibling(file.getName() + ".tmp");
            try {
                Files.createDirectories(target.toAbsolutePath().getParent());
                Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);
                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not save cooldowns.yml.", e);
            }
        }
    }
}
