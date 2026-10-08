/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.placeholders;

import com.joshlucem.skylease.Skylease;
import com.joshlucem.skylease.managers.MessageManager;
import com.joshlucem.skylease.models.ParticleEffect;
import com.joshlucem.skylease.models.PlayerData;
import com.joshlucem.skylease.utils.Credits;
import com.joshlucem.skylease.utils.TimeUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;

/** %skylease_...%, listed in the README. Only answers for online players, whose data is in memory. */
public class SkyleasePlaceholder extends PlaceholderExpansion {

    private final Skylease plugin;

    public SkyleasePlaceholder(Skylease plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "skylease";
    }

    @Override
    public @NotNull String getAuthor() {
        return Credits.AUTHOR;
    }

    @Override
    public @NotNull String getVersion() {
        return Credits.version();
    }

    @Override
    public boolean persist() {
        return true;
    }

    private MessageManager messages() {
        return plugin.getMessageManager();
    }

    @Override
    public String onRequest(OfflinePlayer offline, @NotNull String params) {
        if (offline == null) {
            return "";
        }
        PlayerData data = plugin.getDatabaseManager().getCachedPlayerData(offline.getUniqueId());
        if (data == null) {
            return "";
        }

        return switch (params.toLowerCase()) {
            case "fly-time" -> messages().displayTime(offline.getPlayer(), data.getFlyTime());
            case "fly-time-seconds" -> String.valueOf(data.getFlyTime());
            case "fly-has-time" -> yesNo(data.hasFlyTime() || unlimited(offline));
            case "fly-unlimited" -> yesNo(unlimited(offline));
            case "fly-status" -> messages().raw(isFlying(offline) ? "placeholders.flying" : "placeholders.grounded");
            case "fly-flying" -> String.valueOf(isFlying(offline));
            case "fly-effect" -> data.getParticleEffect();
            case "fly-effect-name" -> effectName(data.getParticleEffect());
            case "fly-alerts" -> String.valueOf(data.getEnabledAlerts().size());
            case "fly-alerts-list" -> alertList(data);
            case "fly-percentage" -> unlimited(offline) ? "100%" : percentage(data.getFlyTime());
            case "fly-bar" -> bar(unlimited(offline) ? plugin.getConfigManager().getPlaceholderMaxFlySeconds() : data.getFlyTime());
            case "fly-time-color" -> unlimited(offline) ? "&a" : timeColor(data.getFlyTime());
            default -> null;
        };
    }

    private boolean unlimited(OfflinePlayer offline) {
        Player player = offline.getPlayer();
        return player != null && player.hasPermission("skylease.unlimited");
    }

    private boolean isFlying(OfflinePlayer offline) {
        return offline instanceof Player player && plugin.getFlightManager().isFlightManaged(player) && player.isFlying();
    }

    private String yesNo(boolean value) {
        return messages().raw(value ? "placeholders.yes" : "placeholders.no");
    }

    private String effectName(String id) {
        ParticleEffect effect = id == null ? null : plugin.getParticleManager().getEffect(id);
        if (effect == null) {
            return messages().raw("placeholders.none");
        }
        return plugin.getParticleManager().displayName(effect).replaceAll("<[^>]+>", "");
    }

    private String alertList(PlayerData data) {
        if (data.getEnabledAlerts().isEmpty()) {
            return messages().raw("placeholders.no-alerts");
        }
        return data.getEnabledAlerts().stream()
            .sorted()
            .map(seconds -> seconds >= 60 ? (seconds / 60) + "m" : seconds + "s")
            .collect(Collectors.joining(", "));
    }

    private String percentage(long seconds) {
        long max = plugin.getConfigManager().getPlaceholderMaxFlySeconds();
        if (max <= 0) {
            return "0%";
        }
        // clamp first so the multiplication cannot overflow
        return Math.min(seconds, max) * 100 / max + "%";
    }

    private String bar(long seconds) {
        long max = plugin.getConfigManager().getPlaceholderMaxFlySeconds();
        int length = plugin.getConfigManager().getPlaceholderBarLength();
        int filled = max <= 0 ? 0 : (int) (Math.min(seconds, max) * length / max);

        return "[" + plugin.getConfigManager().getPlaceholderBarFilled().repeat(filled)
            + plugin.getConfigManager().getPlaceholderBarEmpty().repeat(length - filled) + "]";
    }

    // green above 30 minutes, yellow above 5, red below
    private String timeColor(long seconds) {
        if (seconds > 30 * 60) {
            return "&a";
        }
        return seconds > 5 * 60 ? "&e" : "&c";
    }
}
