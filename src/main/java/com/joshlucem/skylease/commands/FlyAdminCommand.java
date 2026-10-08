/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.commands;

import com.joshlucem.skylease.Skylease;
import com.joshlucem.skylease.managers.MessageManager;
import com.joshlucem.skylease.models.ParticleEffect;
import com.joshlucem.skylease.models.PlayerData;
import com.joshlucem.skylease.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Administration commands: add, remove, info, reload and debug. */
public class FlyAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("add", "remove", "info", "reload", "debug");
    private static final List<String> TIME_EXAMPLES = List.of("10s", "1m", "5m", "30m", "1h", "6h", "1d", "7d", "1mo", "1y");
    private static final List<String> TIME_UNITS = List.of("s", "m", "h", "d", "mo", "y");

    private final Skylease plugin;

    public FlyAdminCommand(Skylease plugin) {
        this.plugin = plugin;
    }

    private MessageManager messages() {
        return plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages().sendList(sender, "admin.usage");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!SUBCOMMANDS.contains(sub)) {
            messages().send(sender, "admin.invalid-arg", MessageManager.text("arg", args[0]));
            messages().sendList(sender, "admin.usage");
            return true;
        }
        if (!sender.hasPermission(permissionFor(sub))) {
            messages().send(sender, "general.no-permission");
            return true;
        }

        switch (sub) {
            case "add" -> add(sender, args);
            case "remove" -> remove(sender, args);
            case "info" -> info(sender, args);
            case "reload" -> reload(sender);
            default -> debug(sender, args);
        }
        return true;
    }

    private String permissionFor(String sub) {
        return sub.equals("debug") ? "skylease.admin.debug" : "skylease.admin.command." + sub;
    }

    private void add(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages().send(sender, "admin.usage-add");
            return;
        }
        OfflinePlayer target = resolve(sender, args[1]);
        if (target == null) {
            return;
        }
        long seconds = parseOrComplain(sender, args[2]);
        if (seconds <= 0) {
            return;
        }

        plugin.getDatabaseManager().updateFlyTime(target.getUniqueId(), time -> time + seconds)
            .thenAccept(total -> sync(() -> {
                audit(sender, "added " + TimeUtil.formatTime(seconds) + " to " + name(target, args[1]));
                messages().send(sender, "admin.time-added",
                    MessageManager.text("player", name(target, args[1])),
                    MessageManager.text("time", TimeUtil.formatTime(seconds)));
                notifyTarget(sender, target, "admin.time-added-notify-admin", "admin.time-added-notify", seconds, args[1]);
            }))
            .exceptionally(error -> failed(sender, error));
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages().send(sender, "admin.usage-remove");
            return;
        }
        OfflinePlayer target = resolve(sender, args[1]);
        if (target == null) {
            return;
        }
        Player online = target.getPlayer();
        if (online != null && online.hasPermission("skylease.unlimited")) {
            messages().send(sender, "admin.cannot-remove-unlimited", MessageManager.text("player", name(target, args[1])));
            return;
        }

        boolean everything = args.length == 2;
        long seconds = everything ? 0 : parseOrComplain(sender, args[2]);
        if (!everything && seconds <= 0) {
            return;
        }

        plugin.getDatabaseManager().updateFlyTime(target.getUniqueId(), time -> everything ? 0 : time - seconds)
            .thenAccept(total -> sync(() -> {
                String playerName = name(target, args[1]);
                if (everything) {
                    audit(sender, "removed all the time of " + playerName);
                    messages().send(sender, "admin.all-time-removed", MessageManager.text("player", playerName));
                    notifyTarget(sender, target, "admin.all-time-removed-notify-admin", "admin.all-time-removed-notify", 0, args[1]);
                } else {
                    audit(sender, "removed " + TimeUtil.formatTime(seconds) + " from " + playerName);
                    messages().send(sender, "admin.time-removed",
                        MessageManager.text("player", playerName),
                        MessageManager.text("time", TimeUtil.formatTime(seconds)));
                    notifyTarget(sender, target, "admin.time-removed-notify-admin", "admin.time-removed-notify", seconds, args[1]);
                }
                Player now = target.getPlayer();
                if (total <= 0 && now != null && plugin.getFlightManager().isFlightManaged(now)) {
                    plugin.getFlightManager().disableFlight(now);
                }
            }))
            .exceptionally(error -> failed(sender, error));
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages().send(sender, "admin.usage-info");
            return;
        }
        OfflinePlayer target = resolve(sender, args[1]);
        if (target == null) {
            return;
        }

        plugin.getDatabaseManager().getPlayerDataAsync(target.getUniqueId())
            .thenAccept(data -> sync(() -> sendInfo(sender, target, args[1], data)))
            .exceptionally(error -> failed(sender, error));
    }

    private void sendInfo(CommandSender sender, OfflinePlayer target, String typedName, PlayerData data) {
        Player online = target.getPlayer();
        boolean flying = online != null && plugin.getFlightManager().isFlightManaged(online) && online.isFlying();
        messages().sendList(sender, "admin.player-info",
            MessageManager.text("player", name(target, typedName)),
            MessageManager.text("time", messages().displayTime(target, data.getFlyTime())),
            MessageManager.mini("status", messages().raw(flying ? "admin.status-flying" : "admin.status-grounded")),
            MessageManager.mini("effect", effectLabel(data.getParticleEffect())),
            MessageManager.text("alerts", data.getEnabledAlerts().size()),
            MessageManager.text("max_alerts", plugin.getConfigManager().getAvailableAlertThresholds().size()),
            MessageManager.text("uuid", target.getUniqueId()),
            MessageManager.text("last_seen", TimeUtil.formatTimestamp(data.getLastSeen())));
    }

    private String effectLabel(String id) {
        ParticleEffect effect = plugin.getParticleManager().getEffect(id);
        return effect != null ? plugin.getParticleManager().displayName(effect) : messages().raw("menus.effects.none");
    }

    private void reload(CommandSender sender) {
        try {
            plugin.reloadConfigs();
            messages().send(sender, "general.config-reloaded");
        } catch (RuntimeException e) {
            messages().send(sender, "general.reload-failed");
            plugin.log().error("Could not reload the configuration: " + e.getMessage(), e);
        }
    }

    private void debug(CommandSender sender, String[] args) {
        String state = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (state) {
            case "on" -> {
                plugin.log().setDebug(true);
                plugin.log().info("Debug mode enabled.");
                messages().send(sender, "debug.enabled");
            }
            case "off" -> {
                plugin.log().setDebug(false);
                plugin.log().info("Debug mode disabled.");
                messages().send(sender, "debug.disabled");
            }
            default -> messages().send(sender, "debug.invalid-arg");
        }
    }

    private OfflinePlayer resolve(CommandSender sender, String name) {
        // getOfflinePlayerIfCached avoids asking Mojang from the main thread
        OfflinePlayer target = Bukkit.getPlayerExact(name);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(name);
        }
        if (target == null) {
            messages().send(sender, "general.player-never-joined", MessageManager.text("player", name));
        }
        return target;
    }

    private long parseOrComplain(CommandSender sender, String input) {
        long seconds = TimeUtil.parseTime(input);
        if (seconds <= 0) {
            messages().send(sender, "admin.invalid-time", MessageManager.text("time", input));
            messages().send(sender, "general.time-help");
        }
        return seconds;
    }

    private String name(OfflinePlayer target, String fallback) {
        return target.getName() != null ? target.getName() : fallback;
    }

    private void notifyTarget(CommandSender sender, OfflinePlayer target, String byAdmin, String generic, long seconds, String typedName) {
        Player online = target.getPlayer();
        if (online == null) {
            messages().send(sender, "admin.player-offline-note", MessageManager.text("player", name(target, typedName)));
            return;
        }
        messages().send(online, sender instanceof Player ? byAdmin : generic,
            MessageManager.text("admin", sender.getName()),
            MessageManager.text("time", TimeUtil.formatTime(seconds)));
    }

    private void audit(CommandSender sender, String action) {
        plugin.log().info(sender.getName() + " " + action);
    }

    private Void failed(CommandSender sender, Throwable error) {
        plugin.log().error("Database error in /flyadmin: " + error.getMessage());
        sync(() -> messages().send(sender, "general.database-error"));
        return null;
    }

    // the database answers on another thread and Bukkit is only touched from the main one
    private void sync(Runnable action) {
        Bukkit.getScheduler().runTask(plugin, action);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("skylease.admin.use")) {
            return List.of();
        }
        if (args.length == 1) {
            return filter(SUBCOMMANDS.stream().filter(sub -> sender.hasPermission(permissionFor(sub))).toList(), args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && List.of("add", "remove", "info").contains(sub)) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 2 && sub.equals("debug")) {
            return filter(List.of("on", "off"), args[1]);
        }
        if (args.length == 3 && (sub.equals("add") || sub.equals("remove"))) {
            return timeSuggestions(args[2].toLowerCase(Locale.ROOT));
        }
        return List.of();
    }

    private List<String> timeSuggestions(String typed) {
        if (typed.isEmpty()) {
            return TIME_EXAMPLES;
        }
        if (typed.chars().allMatch(Character::isDigit)) {
            List<String> withUnits = new ArrayList<>();
            TIME_UNITS.forEach(unit -> withUnits.add(typed + unit));
            return withUnits;
        }
        return filter(TIME_EXAMPLES, typed);
    }

    private List<String> filter(List<String> options, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
