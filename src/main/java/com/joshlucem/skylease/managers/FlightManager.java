/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.managers;

import com.joshlucem.skylease.Skylease;
import com.joshlucem.skylease.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Managed" means Skylease gave the player flight. If it comes from creative
 * or another plugin, it is left alone and not charged.
 */
public class FlightManager {

    private final Skylease plugin;
    private final Set<UUID> managed = ConcurrentHashMap.newKeySet();
    private final Set<UUID> softLanding = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BukkitTask> softLandingTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastToggle = new ConcurrentHashMap<>();
    // allowFlight before we switched it on
    private final Map<UUID, Boolean> previousAllowFlight = new ConcurrentHashMap<>();
    private BukkitTask flightTask;

    public FlightManager(Skylease plugin) {
        this.plugin = plugin;
    }

    public boolean enableFlight(Player player) {
        return activate(player, false);
    }

    public boolean restoreFlight(Player player) {
        return activate(player, true);
    }

    private boolean activate(Player player, boolean restoring) {
        UUID uuid = player.getUniqueId();
        if (isCreativeOrSpectator(player)) {
            if (!restoring) {
                plugin.getMessageManager().send(player, "fly.creative");
            }
            return false;
        }
        cancelSoftLanding(uuid);

        if (!restoring && isOnCooldown(player)) {
            return false;
        }
        if (!isWorldAllowed(player)) {
            if (!restoring) {
                plugin.getMessageManager().send(player, "fly.world-blocked");
            }
            return false;
        }
        if (!isRegionAllowed(player)) {
            if (!restoring) {
                plugin.getMessageManager().send(player, "fly.region-blocked");
            }
            return false;
        }

        if (!managed.contains(uuid)) {
            // on reconnect the server may still have allowFlight=true from the last session
            previousAllowFlight.put(uuid, !restoring && player.getAllowFlight());
        }
        managed.add(uuid);
        long stamp = System.currentTimeMillis();
        lastToggle.put(uuid, stamp);
        player.setAllowFlight(true);
        player.setFlying(true);

        // the client sometimes undoes flight in the same tick
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // only if nothing has toggled flight since this activation
            if (player.isOnline() && managed.contains(uuid) && lastToggle.get(uuid) == stamp) {
                player.setAllowFlight(true);
                player.setFlying(true);
            }
        }, 1L);

        setPersistedFlying(uuid, true);
        plugin.log().debug("Flight enabled for " + player.getName() + (restoring ? " (restored)" : ""));
        return true;
    }

    private boolean isCreativeOrSpectator(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }

    private boolean isOnCooldown(Player player) {
        int cooldown = plugin.getConfigManager().getFlyToggleCooldownSeconds();
        if (cooldown <= 0 || player.hasPermission("skylease.user.cooldown.bypass")) {
            return false;
        }
        long elapsed = System.currentTimeMillis() - lastToggle.getOrDefault(player.getUniqueId(), 0L);
        long remainingMillis = cooldown * 1000L - elapsed;
        if (remainingMillis <= 0) {
            return false;
        }
        long seconds = (remainingMillis + 999L) / 1000L;
        plugin.getMessageManager().send(player, "fly.cooldown-active", MessageManager.text("seconds", seconds));
        return true;
    }

    public void disableFlight(Player player) {
        disableFlight(player, false);
    }

    public void disableFlight(Player player, boolean depleted) {
        disableFlight(player, depleted, "fly.soft-landing-start");
    }

    /** @param softLandingMessage key of the message sent if a gentle landing starts */
    public void disableFlight(Player player, boolean depleted, String softLandingMessage) {
        UUID uuid = player.getUniqueId();
        if (!managed.remove(uuid)) {
            return;
        }

        if (depleted && plugin.getConfigManager().isSoftLandingEnabled() && !isOnGround(player)) {
            startSoftLanding(player, softLandingMessage);
        } else {
            restoreFlags(player);
        }

        setPersistedFlying(uuid, false);
        lastToggle.put(uuid, System.currentTimeMillis());
        plugin.log().debug("Flight disabled for " + player.getName());
    }

    // removes Skylease's flags without touching the database
    public void releaseOnQuit(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask task = softLandingTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        if (managed.remove(uuid) || softLanding.remove(uuid)) {
            restoreFlags(player);
        }
        player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        previousAllowFlight.remove(uuid);
        // kept while the cooldown is still running, so relogging does not skip it
        long cooldownMillis = plugin.getConfigManager().getFlyToggleCooldownSeconds() * 1000L;
        if (System.currentTimeMillis() - lastToggle.getOrDefault(uuid, 0L) >= cooldownMillis) {
            lastToggle.remove(uuid);
        }
    }

    public void disableAllFlights() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (managed.contains(player.getUniqueId()) || softLanding.contains(player.getUniqueId())) {
                releaseOnQuit(player);
            }
        }
        managed.clear();
        softLanding.clear();
    }

    private void restoreFlags(Player player) {
        UUID uuid = player.getUniqueId();
        boolean restoreAllow = previousAllowFlight.getOrDefault(uuid, false);
        previousAllowFlight.remove(uuid);
        player.setFlying(false);
        if (plugin.getConfigManager().isSoftLandingUseElytraGlide()) {
            player.setGliding(false);
        }
        player.setAllowFlight(restoreAllow);
    }

    private void setPersistedFlying(UUID uuid, boolean flying) {
        PlayerData data = plugin.getDatabaseManager().getCachedPlayerData(uuid);
        if (data != null) {
            data.setFlying(flying);
            plugin.getDatabaseManager().markDirty(uuid);
        }
    }

    public void reapplyManagedFlight(Player player) {
        UUID uuid = player.getUniqueId();
        if (!player.isOnline() || !managed.contains(uuid) || softLanding.contains(uuid)) {
            return;
        }
        player.setAllowFlight(true);
        if (!player.isFlying()) {
            player.setFlying(true);
        }
    }

    private void startSoftLanding(Player player, String messageKey) {
        UUID uuid = player.getUniqueId();
        if (!softLanding.add(uuid)) {
            return;
        }

        plugin.getMessageManager().send(player, messageKey);

        int duration = plugin.getConfigManager().getSoftLandingDurationTicks();
        boolean slowFalling = plugin.getConfigManager().isSoftLandingUseSlowFalling();
        boolean glide = plugin.getConfigManager().isSoftLandingUseElytraGlide();
        double fallSpeed = plugin.getConfigManager().getSoftLandingFallSpeed();

        if (slowFalling) {
            // +40 ticks so it does not end just before touching the ground
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, duration + 40, 0, false, true, true));
        }
        if (glide) {
            player.setGliding(true);
        }
        player.setAllowFlight(true);
        player.setFlying(true);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int ticksLeft = duration;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    finishSoftLanding(uuid);
                    return;
                }
                if (isOnGround(player)) {
                    finishSoftLanding(uuid);
                    plugin.getMessageManager().send(player, "fly.soft-landing-complete");
                    return;
                }
                if (ticksLeft <= 0) {
                    finishSoftLanding(uuid);
                    // extra margin against damage on the last stretch
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, false, false, false));
                    return;
                }

                if (!slowFalling && !glide) {
                    Vector velocity = player.getVelocity();
                    if (velocity.getY() < -fallSpeed) {
                        velocity.setY(-fallSpeed);
                        player.setVelocity(velocity);
                    }
                }
                // halfway down flight is taken away and they drift down
                if (ticksLeft == duration / 2) {
                    player.setFlying(false);
                }
                ticksLeft--;
            }
        }, 0L, 1L);
        softLandingTasks.put(uuid, task);
    }

    private void finishSoftLanding(UUID uuid) {
        softLanding.remove(uuid);
        BukkitTask task = softLandingTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            restoreFlags(player);
        } else {
            previousAllowFlight.remove(uuid);
        }
    }

    private void cancelSoftLanding(UUID uuid) {
        if (softLanding.contains(uuid)) {
            finishSoftLanding(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.removePotionEffect(PotionEffectType.SLOW_FALLING);
            }
        }
    }

    private boolean isOnGround(Player player) {
        Block below = player.getLocation().clone().subtract(0, 0.1, 0).getBlock();
        Material type = below.getType();
        return type.isSolid() || type == Material.WATER || type == Material.LAVA || player.isOnGround();
    }

    public boolean isInSoftLanding(UUID uuid) {
        return softLanding.contains(uuid);
    }

    private boolean hasBypass(Player player) {
        return player.hasPermission("skylease.admin.bypass-restrictions");
    }

    public boolean isWorldAllowed(World world) {
        String name = world.getName();
        if (plugin.getConfigManager().getBlockedWorlds().contains(name)) {
            return false;
        }
        List<String> allowed = plugin.getConfigManager().getAllowedWorlds();
        return allowed.isEmpty() || allowed.contains(name);
    }

    public boolean isWorldAllowed(Player player) {
        return hasBypass(player) || isWorldAllowed(player.getWorld());
    }

    private boolean isRegionAllowed(Player player) {
        WorldGuardManager worldGuard = plugin.getWorldGuardManager();
        return hasBypass(player) || worldGuard == null || !worldGuard.isEnabled() || worldGuard.isFlightAllowedAt(player);
    }

    public void startFlightTask() {
        int interval = plugin.getConfigManager().getFlightConsumeIntervalTicks();
        flightTask = Bukkit.getScheduler().runTaskTimer(plugin, this::consumeTick, interval, interval);
    }

    public void restartTask() {
        stopTasks();
        startFlightTask();
    }

    public void stopTasks() {
        if (flightTask != null) {
            flightTask.cancel();
            flightTask = null;
        }
    }

    private void consumeTick() {
        int amount = plugin.getConfigManager().getFlightConsumeAmountSeconds();
        boolean autoDisable = plugin.getConfigManager().isAutoDisableOnDepleted();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!managed.contains(uuid) || softLanding.contains(uuid) || !player.isFlying() || isCreativeOrSpectator(player)) {
                continue;
            }
            // a region can deny flight even to someone already in the air
            if (!isRegionAllowed(player)) {
                boolean airborne = !isOnGround(player);
                disableFlight(player, true, "fly.region-entered");
                if (!airborne) {
                    plugin.getMessageManager().send(player, "fly.region-blocked");
                }
                continue;
            }
            if (player.hasPermission("skylease.unlimited")) {
                continue;
            }

            PlayerData data = plugin.getDatabaseManager().getCachedPlayerData(uuid);
            if (data == null) {
                // without data we cannot charge them, so they do not fly
                disableFlight(player);
                plugin.getMessageManager().send(player, "general.database-error");
                continue;
            }

            long before = data.getFlyTime();
            long after = data.updateFlyTime(time -> time - amount);
            plugin.getDatabaseManager().markDirty(uuid);

            if (before > 0) {
                plugin.getAlertManager().checkAndSendAlert(player, data, before, after);
            }
            if (after <= 0 && autoDisable) {
                disableFlight(player, true);
                plugin.getMessageManager().send(player, "fly.time-depleted");
            }
        }
    }

    public boolean isFlightManaged(Player player) {
        return managed.contains(player.getUniqueId());
    }
}
