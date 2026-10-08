/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.commands;

import com.joshlucem.skylease.Skylease;
import com.joshlucem.skylease.managers.MessageManager;
import com.joshlucem.skylease.models.PlayerData;
import com.joshlucem.skylease.utils.TimeUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class FlyCommand implements CommandExecutor, TabCompleter {

    private final Skylease plugin;

    public FlyCommand(Skylease plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager messages = plugin.getMessageManager();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "general.players-only");
            return true;
        }
        if (!player.hasPermission("skylease.user.fly")) {
            messages.send(player, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            toggle(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "settings" -> {
                if (player.hasPermission("skylease.user.menu.settings")) {
                    plugin.getMenuManager().openSettingsMenu(player);
                } else {
                    messages.send(player, "general.no-permission");
                }
            }
            case "shop" -> messages.sendList(player, "shop.lines");
            default -> messages.send(player, "fly.invalid-args");
        }
        return true;
    }

    private void toggle(Player player) {
        MessageManager messages = plugin.getMessageManager();
        PlayerData data = plugin.getDatabaseManager().getCachedPlayerData(player.getUniqueId());
        if (data == null) {
            messages.send(player, "general.database-error");
            return;
        }

        if (plugin.getFlightManager().isFlightManaged(player)) {
            plugin.getFlightManager().disableFlight(player);
            messages.send(player, "fly.disabled");
            return;
        }

        boolean unlimited = player.hasPermission("skylease.unlimited");
        if (!unlimited && !data.hasFlyTime()) {
            messages.send(player, "fly.no-time");
            messages.send(player, "fly.no-time-shop");
            return;
        }

        // if it fails, enableFlight has already told the player
        if (!plugin.getFlightManager().enableFlight(player)) {
            return;
        }
        if (unlimited) {
            messages.send(player, "fly.unlimited");
        } else {
            messages.send(player, "fly.enabled", MessageManager.text("time", TimeUtil.formatTime(data.getFlyTime())));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String typed = args[0].toLowerCase();
        return List.of("settings", "shop").stream().filter(option -> option.startsWith(typed)).toList();
    }
}
