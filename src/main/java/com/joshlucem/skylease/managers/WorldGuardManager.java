/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.managers;

import com.joshlucem.skylease.Skylease;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import org.bukkit.entity.Player;

/**
 * WorldGuard integration through a region flag: /rg flag &lt;region&gt; skylease-fly deny.
 * Unset regions allow flight. The flag has to be registered in onLoad, before WorldGuard enables.
 */
public class WorldGuardManager {

    public static final String FLAG_NAME = "skylease-fly";

    private static StateFlag flightFlag;

    private final Skylease plugin;
    private final boolean enabled;

    public WorldGuardManager(Skylease plugin) {
        this.plugin = plugin;
        boolean installed = plugin.getServer().getPluginManager().getPlugin("WorldGuard") != null;
        boolean wanted = plugin.getConfigManager().isWorldGuardIntegrationEnabled();
        this.enabled = installed && wanted && flightFlag != null;

        if (enabled) {
            plugin.log().ok("WorldGuard: flag " + FLAG_NAME + " active");
        } else if (installed && wanted) {
            plugin.log().warn("WorldGuard: the " + FLAG_NAME + " flag could not be registered");
        } else {
            plugin.log().info("WorldGuard: integration off");
        }
    }

    /** Call from onLoad, and only when WorldGuard is installed. */
    public static void registerFlag(Skylease plugin) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag flag = new StateFlag(FLAG_NAME, true);
            registry.register(flag);
            flightFlag = flag;
        } catch (FlagConflictException e) {
            // another plugin or an earlier load already registered the name
            Flag<?> existing = registry.get(FLAG_NAME);
            if (existing instanceof StateFlag state) {
                flightFlag = state;
            } else {
                plugin.log().warn("WorldGuard: the name " + FLAG_NAME + " is taken by a different kind of flag");
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** @return false if a region at the player's position denies the flag */
    public boolean isFlightAllowedAt(Player player) {
        if (!enabled) {
            return true;
        }
        try {
            LocalPlayer local = WorldGuardPlugin.inst().wrapPlayer(player);
            return WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                .testState(BukkitAdapter.adapt(player.getLocation()), local, flightFlag);
        } catch (RuntimeException e) {
            // if WorldGuard fails, the player is not blocked
            plugin.log().warn("Could not query WorldGuard: " + e.getMessage());
            return true;
        }
    }
}
