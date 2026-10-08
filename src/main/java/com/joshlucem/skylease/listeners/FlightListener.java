/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.listeners;

import com.joshlucem.skylease.Skylease;
import com.joshlucem.skylease.managers.FlightManager;
import com.joshlucem.skylease.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

public class FlightListener implements Listener {

    private final Skylease plugin;

    public FlightListener(Skylease plugin) {
        this.plugin = plugin;
    }

    private FlightManager flight() {
        return plugin.getFlightManager();
    }

    private boolean isCreativeOrSpectator(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }

    private boolean hasFlyPermission(Player player) {
        return player.hasPermission("skylease.user.fly") || player.hasPermission("skylease.unlimited");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!event.isFlying() || isCreativeOrSpectator(player) || !flight().isFlightManaged(player)) {
            return;
        }
        if (player.hasPermission("skylease.unlimited")) {
            return;
        }

        if (!player.hasPermission("skylease.user.fly")) {
            event.setCancelled(true);
            flight().disableFlight(player);
            plugin.getMessageManager().send(player, "general.no-permission");
            return;
        }

        PlayerData data = plugin.getDatabaseManager().getCachedPlayerData(player.getUniqueId());
        if (data == null || !data.hasFlyTime()) {
            event.setCancelled(true);
            flight().disableFlight(player);
            plugin.getMessageManager().send(player, "fly.no-time");
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        GameMode mode = event.getNewGameMode();
        if ((mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) || !flight().isFlightManaged(player)) {
            return;
        }

        // one tick later, once the game mode change has been applied
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !flight().isFlightManaged(player)) {
                return;
            }
            if (!hasFlyPermission(player)) {
                flight().disableFlight(player);
                plugin.getMessageManager().send(player, "general.no-permission");
            } else if (!player.hasPermission("skylease.unlimited") && !flight().isWorldAllowed(player)) {
                flight().disableFlight(player);
                plugin.getMessageManager().send(player, "general.world-not-allowed");
            } else {
                flight().reapplyManagedFlight(player);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (isCreativeOrSpectator(player) || !flight().isFlightManaged(player)) {
            return;
        }

        if (!player.hasPermission("skylease.unlimited") && !flight().isWorldAllowed(player)) {
            flight().disableFlight(player);
            plugin.getMessageManager().send(player, "general.world-not-allowed");
            return;
        }
        // the server clears the flags on a world change
        Bukkit.getScheduler().runTaskLater(plugin, () -> flight().reapplyManagedFlight(player), 1L);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (isCreativeOrSpectator(player) || !flight().isFlightManaged(player) || player.hasPermission("skylease.unlimited")) {
            return;
        }
        if (event.getTo() == null || event.getTo().getWorld() == null || flight().isWorldAllowed(event.getTo().getWorld())) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // the player is not in the destination world yet
            if (player.isOnline() && flight().isFlightManaged(player) && !flight().isWorldAllowed(player)) {
                flight().disableFlight(player);
                plugin.getMessageManager().send(player, "general.world-not-allowed");
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
            && event.getCause() == EntityDamageEvent.DamageCause.FALL
            && flight().isInSoftLanding(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
