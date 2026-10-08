/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.listeners;

import com.joshlucem.skylease.Skylease;
import com.joshlucem.skylease.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class ConnectionListener implements Listener {

    private final Skylease plugin;

    public ConnectionListener(Skylease plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        if (plugin.getDatabaseManager().loadAndCache(uuid, event.getName()) == null) {
            return;
        }
        // if another plugin refuses the login, nobody would clean this entry up
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (Bukkit.getPlayer(uuid) == null) {
                plugin.getDatabaseManager().discard(uuid);
            }
        }, 20L * 60);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getDatabaseManager().getCachedPlayerData(player.getUniqueId());
        if (data != null) {
            start(player, data);
            return;
        }

        // not loaded: the pre-login load failed, or a previous session of this player left just before.
        // Try once more, off the main thread.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerData loaded = plugin.getDatabaseManager().loadAndCache(player.getUniqueId(), player.getName());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (loaded == null) {
                    plugin.log().warn("No data for " + player.getName() + ": the database did not answer.");
                } else {
                    start(player, loaded);
                }
            });
        });
    }

    private void start(Player player, PlayerData data) {
        data.setUsername(player.getName());
        data.updateLastSeen();
        plugin.getDatabaseManager().markDirty(player.getUniqueId());

        if (!data.isFlying()) {
            return;
        }
        // whoever left flying gets it back, if they still can
        boolean unlimited = player.hasPermission("skylease.unlimited");
        boolean allowed = (player.hasPermission("skylease.user.fly") || unlimited)
            && (unlimited || data.hasFlyTime())
            && plugin.getFlightManager().isWorldAllowed(player);
        if (!allowed || !plugin.getFlightManager().restoreFlight(player)) {
            data.setFlying(false);
            plugin.getDatabaseManager().markDirty(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // the same account logged in again: this is the old session leaving, the new one keeps its state
        Player current = Bukkit.getPlayer(uuid);
        if (current != null && current != player) {
            return;
        }

        PlayerData data = plugin.getDatabaseManager().getCachedPlayerData(uuid);
        if (data != null) {
            boolean unlimited = player.hasPermission("skylease.unlimited");
            // only saved as "flying" if Skylease was managing it
            boolean canKeep = plugin.getFlightManager().isFlightManaged(player)
                && (player.hasPermission("skylease.user.fly") || unlimited)
                && (unlimited || data.hasFlyTime());
            data.setFlying(canKeep);
            data.updateLastSeen();
        }

        // do not leave allowFlight set in the server's player data
        plugin.getFlightManager().releaseOnQuit(player);
        plugin.getMenuManager().forget(uuid);
        plugin.getDatabaseManager().saveAndEvict(uuid);
    }
}
