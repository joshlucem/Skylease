/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.managers;

import com.joshlucem.skylease.Skylease;
import com.joshlucem.skylease.models.PlayerData;
import com.joshlucem.skylease.utils.TimeUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Alerts when a threshold is crossed: above it before, at or below it now. */
public class AlertManager {

    private static final int URGENT_THRESHOLD_SECONDS = 60;

    private final Skylease plugin;

    public AlertManager(Skylease plugin) {
        this.plugin = plugin;
    }

    public void checkAndSendAlert(Player player, PlayerData data, long secondsBefore, long secondsNow) {
        for (int threshold : data.getEnabledAlerts()) {
            if (threshold > 0 && secondsBefore > threshold && secondsNow <= threshold) {
                send(player, secondsNow, threshold);
                plugin.log().debug("Alert at " + threshold + "s sent to " + player.getName());
            }
        }
    }

    private void send(Player player, long remaining, int threshold) {
        String path = threshold <= URGENT_THRESHOLD_SECONDS ? "alerts.very-low-time" : "alerts.low-time";
        plugin.getMessageManager().send(player, path, MessageManager.text("time", TimeUtil.formatTime(remaining)));

        String soundName = plugin.getConfigManager().getAlertSound();
        if (soundName.equalsIgnoreCase("NONE")) {
            return;
        }
        try {
            @SuppressWarnings("deprecation") // Sound ya no es enum
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound,
                plugin.getConfigManager().getAlertSoundVolume(),
                plugin.getConfigManager().getAlertSoundPitch());
        } catch (IllegalArgumentException e) {
            // already warned about when the config loaded
        }
    }
}
