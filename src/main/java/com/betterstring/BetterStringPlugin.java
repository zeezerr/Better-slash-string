package com.betterstring;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Better /String - fills every empty storage slot with string.
 * The enabled state is remembered across restarts (state.yml) unless
 * remember-enabled-state is set to false in config.yml.
 */
public final class BetterStringPlugin extends JavaPlugin {

    public static final String PERM_USE = "betterstring.use";
    public static final String PERM_ADMIN = "betterstring.admin";
    public static final String PERM_BYPASS = "betterstring.cooldown.bypass";

    private static final String DEFAULT_PREFIX = "&8[&bBetter &f/String&8] ";
    private static final int DEFAULT_COOLDOWN_SECONDS = 30;
    private static final int MAX_COOLDOWN_SECONDS = 31_536_000; // 1 year
    private static final int DEFAULT_STACK_SIZE = 64;
    private static final long CLEANUP_INTERVAL_TICKS = 20L * 60L * 5L;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    /** Starts false; restored from state.yml in onEnable when remember-enabled-state is true. */
    private final AtomicBoolean featureEnabled = new AtomicBoolean(false);

    private volatile String prefix = DEFAULT_PREFIX;
    private volatile long cooldownMillis = DEFAULT_COOLDOWN_SECONDS * 1000L;
    private volatile int stackSize = DEFAULT_STACK_SIZE;
    private volatile boolean adminBypassCooldown = false;
    private volatile boolean cooldownsPersist = true;
    private volatile boolean rememberEnabledState = true;

    private CooldownManager cooldowns;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        featureEnabled.set(rememberEnabledState && loadSavedState());

        cooldowns = new CooldownManager(this, new File(getDataFolder(), "cooldowns.yml"), cooldownsPersist);
        cooldowns.load(cooldownMillis);

        PluginCommand command = getCommand("string");
        if (command == null) {
            getLogger().severe("Command 'string' is missing from plugin.yml - disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        StringCommand handler = new StringCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);

        getServer().getScheduler().runTaskTimerAsynchronously(
                this, cooldowns::cleanup, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);

        getLogger().info("Better /String loaded. The /string feature is currently "
                + (featureEnabled.get() ? "ENABLED" : "DISABLED") + ".");
    }

    @Override
    public void onDisable() {
        if (cooldowns != null) {
            cooldowns.saveNow();
        }
    }

    private void loadSettings() {
        FileConfiguration cfg = getConfig();

        String configuredPrefix = cfg.getString("prefix");
        prefix = configuredPrefix != null ? configuredPrefix : DEFAULT_PREFIX;

        int seconds = DEFAULT_COOLDOWN_SECONDS;
        if (cfg.isInt("cooldown-seconds")) {
            int value = cfg.getInt("cooldown-seconds");
            if (value >= 0 && value <= MAX_COOLDOWN_SECONDS) {
                seconds = value;
            } else {
                warnInvalid("cooldown-seconds", value, DEFAULT_COOLDOWN_SECONDS);
            }
        } else {
            warnInvalid("cooldown-seconds", cfg.get("cooldown-seconds"), DEFAULT_COOLDOWN_SECONDS);
        }
        cooldownMillis = seconds * 1000L;

        int maxStack = Math.min(DEFAULT_STACK_SIZE, Material.STRING.getMaxStackSize());
        int size = maxStack;
        if (cfg.isInt("stack-size")) {
            int value = cfg.getInt("stack-size");
            if (value >= 1 && value <= Material.STRING.getMaxStackSize()) {
                size = value;
            } else {
                warnInvalid("stack-size", value, maxStack);
            }
        } else {
            warnInvalid("stack-size", cfg.get("stack-size"), maxStack);
        }
        stackSize = size;

        cooldownsPersist = cfg.getBoolean("cooldowns-persist", true);
        adminBypassCooldown = cfg.getBoolean("admins-bypass-cooldown", false);
        rememberEnabledState = cfg.getBoolean("remember-enabled-state", true);
    }

    private void warnInvalid(String key, Object value, Object fallback) {
        getLogger().warning("Invalid value '" + value + "' for '" + key + "' in config.yml; using " + fallback + ".");
    }

    /** Sends a configured message (prefix + text, & colour codes, optional placeholder pairs). */
    public void send(CommandSender target, String key, String... replacements) {
        String text = getConfig().getString("messages." + key);
        if (text == null) {
            text = "&cMissing message: " + key;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        target.sendMessage(LEGACY.deserialize(prefix + text));
    }

    public boolean isFeatureEnabled() {
        return featureEnabled.get();
    }

    /** @return true if the state actually changed. */
    public boolean setFeatureEnabled(boolean enabled) {
        boolean changed = featureEnabled.compareAndSet(!enabled, enabled);
        if (changed && rememberEnabledState) {
            saveState(enabled);
        }
        return changed;
    }

    private File stateFile() {
        return new File(getDataFolder(), "state.yml");
    }

    private boolean loadSavedState() {
        File file = stateFile();
        if (!file.exists()) {
            return false;
        }
        try {
            return YamlConfiguration.loadConfiguration(file).getBoolean("enabled", false);
        } catch (RuntimeException e) {
            getLogger().log(Level.WARNING, "state.yml could not be read; starting disabled.", e);
            return false;
        }
    }

    private void saveState(boolean enabled) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", enabled);
        try {
            getDataFolder().mkdirs();
            yaml.save(stateFile());
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not save state.yml.", e);
        }
    }

    public CooldownManager getCooldowns() {
        return cooldowns;
    }

    public long getCooldownMillis() {
        return cooldownMillis;
    }

    public int getStackSize() {
        return stackSize;
    }

    public boolean isAdminBypassCooldown() {
        return adminBypassCooldown;
    }
}
