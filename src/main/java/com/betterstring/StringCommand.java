package com.betterstring;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class StringCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("enable", "disable", "status");

    private final BetterStringPlugin plugin;

    public StringCommand(BetterStringPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            handleUse(sender);
            return true;
        }
        if (args.length > 1) {
            plugin.send(sender, "usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "enable" -> handleEnable(sender);
            case "disable" -> handleDisable(sender);
            case "status" -> handleStatus(sender);
            default -> plugin.send(sender, "usage");
        }
        return true;
    }

    private void handleUse(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "player-only");
            return;
        }
        if (!player.hasPermission(BetterStringPlugin.PERM_USE)) {
            plugin.send(player, "no-permission");
            return;
        }
        if (!plugin.isFeatureEnabled()) {
            plugin.send(player, "feature-disabled");
            return;
        }

        UUID id = player.getUniqueId();
        boolean bypass = player.hasPermission(BetterStringPlugin.PERM_BYPASS)
                || (plugin.isAdminBypassCooldown() && player.hasPermission(BetterStringPlugin.PERM_ADMIN));
        CooldownManager cooldowns = plugin.getCooldowns();

        if (!bypass) {
            long remainingMillis = cooldowns.getRemainingMillis(id);
            if (remainingMillis > 0L) {
                long seconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
                plugin.send(player, "cooldown", "%seconds%", String.valueOf(seconds));
                return;
            }
        }

        // Storage contents = hotbar + main inventory only (36 slots).
        // Armour, offhand and crafting slots are not part of it.
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        List<Integer> emptySlots = new ArrayList<>();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack stack = storage[slot];
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                emptySlots.add(slot);
            }
        }

        if (emptySlots.isEmpty()) {
            plugin.send(player, "no-empty-slots"); // no cooldown started
            return;
        }

        int amount = plugin.getStackSize();
        for (int slot : emptySlots) {
            inventory.setItem(slot, new ItemStack(Material.STRING, amount)); // only touches empty slots
        }

        if (!bypass) {
            cooldowns.start(id, plugin.getCooldownMillis());
        }
        plugin.send(player, "success");
    }

    private void handleEnable(CommandSender sender) {
        if (!sender.hasPermission(BetterStringPlugin.PERM_ADMIN)) {
            plugin.send(sender, "no-permission");
            return;
        }
        plugin.send(sender, plugin.setFeatureEnabled(true) ? "enabled" : "already-enabled");
    }

    private void handleDisable(CommandSender sender) {
        if (!sender.hasPermission(BetterStringPlugin.PERM_ADMIN)) {
            plugin.send(sender, "no-permission");
            return;
        }
        plugin.send(sender, plugin.setFeatureEnabled(false) ? "admin-disabled" : "already-disabled");
    }

    private void handleStatus(CommandSender sender) {
        if (!sender.hasPermission(BetterStringPlugin.PERM_ADMIN)) {
            plugin.send(sender, "no-permission");
            return;
        }
        plugin.send(sender, plugin.isFeatureEnabled() ? "status-enabled" : "status-disabled");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(BetterStringPlugin.PERM_ADMIN)) {
            return List.of();
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String sub : SUBCOMMANDS) {
            if (sub.startsWith(typed)) {
                matches.add(sub);
            }
        }
        return matches;
    }
}
