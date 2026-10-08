/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.managers;

import com.joshlucem.skylease.Skylease;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class ConfigManager {

    private final Skylease plugin;
    private FileConfiguration config;

    public ConfigManager(Skylease plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
        sanitize();
    }

    private void sanitize() {
        fixMin("flight.consume-interval-ticks", 1, 20);
        fixMin("flight.consume-amount-seconds", 0, 1);
        fixMin("flight.fly-toggle-cooldown-seconds", 0, 0);
        fixMin("flight.soft-landing.duration-ticks", 20, 100);
        fixMin("database.save-interval-seconds", 5, 30);
        fixMin("database.auto-purge.interval-hours", 1, 24);
        fixMin("particles.spawn-interval", 1, 5);
        fixMin("particles.particles-per-spawn", 1, 3);

        double volume = config.getDouble("alerts.alert-sound-volume", 1.0);
        if (volume < 0 || volume > 2) {
            warn("alerts.alert-sound-volume", "must be between 0 and 2", "1.0");
            config.set("alerts.alert-sound-volume", 1.0);
        }

        String sound = getAlertSound();
        if (!sound.equalsIgnoreCase("NONE") && !isValidSound(sound)) {
            warn("alerts.alert-sound", "the sound \"" + sound + "\" does not exist", "ENTITY_EXPERIENCE_ORB_PICKUP");
            config.set("alerts.alert-sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        }

        for (int threshold : getAvailableAlertThresholds()) {
            if (threshold <= 0) {
                plugin.log().warn("config.yml: alerts.available-thresholds contains an invalid value (" + threshold + "); it will be ignored.");
            }
        }

        for (int enabled : getDefaultEnabledAlerts()) {
            if (!getAvailableAlertThresholds().contains(enabled)) {
                plugin.log().warn("config.yml: alerts.default-enabled has " + enabled + ", which is not in available-thresholds.");
            }
        }

        String type = getDatabaseType();
        if (!type.equalsIgnoreCase("sqlite") && !type.equalsIgnoreCase("mysql") && !type.equalsIgnoreCase("mariadb")) {
            warn("database.type", "\"" + type + "\" is not a known type", "sqlite");
            config.set("database.type", "sqlite");
        }
    }

    @SuppressWarnings("deprecation") // Sound is no longer an enum
    private boolean isValidSound(String name) {
        try {
            Sound.valueOf(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void fixMin(String path, int min, int fallback) {
        if (config.getInt(path, fallback) < min) {
            warn(path, "must be " + min + " or more", String.valueOf(fallback));
            config.set(path, fallback);
        }
    }

    private void warn(String path, String problem, String replacement) {
        plugin.log().warn("config.yml: " + path + " " + problem + ". Using " + replacement + ".");
    }

    public String getLanguage() {
        String language = config.getString("language.selected", "en").trim().toLowerCase();
        // simple names only, so a typo cannot point outside the lang folder
        return language.matches("[a-z0-9_\\-]+") ? language : "en";
    }

    public String getDatabaseType() {
        return config.getString("database.type", "sqlite");
    }

    public String getDatabaseHost() {
        return config.getString("database.mysql.host", "localhost");
    }

    public int getDatabasePort() {
        return config.getInt("database.mysql.port", 3306);
    }

    public String getDatabaseName() {
        return config.getString("database.mysql.database", "skylease");
    }

    public String getDatabaseUsername() {
        return config.getString("database.mysql.username", "root");
    }

    public String getDatabasePassword() {
        return config.getString("database.mysql.password", "");
    }

    public boolean isDatabaseUseSSL() {
        return config.getBoolean("database.mysql.use-ssl", false);
    }

    public int getSaveIntervalSeconds() {
        return config.getInt("database.save-interval-seconds", 30);
    }

    public boolean isDatabaseAutoPurgeEnabled() {
        return config.getBoolean("database.auto-purge.enabled", true);
    }

    public int getDatabasePurgeDaysInactive() {
        return config.getInt("database.auto-purge.days-inactive", 180);
    }

    public boolean isDatabasePurgeOnStartup() {
        return config.getBoolean("database.auto-purge.on-startup", true);
    }

    public boolean isPurgeKeepingPlayersWithTime() {
        return config.getBoolean("database.auto-purge.keep-players-with-time", true);
    }

    public int getDatabasePurgeIntervalHours() {
        return config.getInt("database.auto-purge.interval-hours", 24);
    }

    public int getFlightConsumeIntervalTicks() {
        return config.getInt("flight.consume-interval-ticks", 20);
    }

    public int getFlightConsumeAmountSeconds() {
        return config.getInt("flight.consume-amount-seconds", 1);
    }

    public int getFlyToggleCooldownSeconds() {
        return config.getInt("flight.fly-toggle-cooldown-seconds", 2);
    }

    public boolean isAutoDisableOnDepleted() {
        return config.getBoolean("flight.auto-disable-on-depleted", true);
    }

    public List<String> getAllowedWorlds() {
        return config.getStringList("flight.allowed-worlds");
    }

    public List<String> getBlockedWorlds() {
        return config.getStringList("flight.blocked-worlds");
    }

    public boolean isWorldGuardIntegrationEnabled() {
        return config.getBoolean("flight.worldguard-integration", true);
    }

    public boolean isSoftLandingEnabled() {
        return config.getBoolean("flight.soft-landing.enabled", true);
    }

    public int getSoftLandingDurationTicks() {
        return config.getInt("flight.soft-landing.duration-ticks", 100);
    }

    public boolean isSoftLandingUseSlowFalling() {
        return config.getBoolean("flight.soft-landing.use-slow-falling", true);
    }

    public boolean isSoftLandingUseElytraGlide() {
        return config.getBoolean("flight.soft-landing.use-elytra-glide", false);
    }

    public double getSoftLandingFallSpeed() {
        return config.getDouble("flight.soft-landing.fall-speed", 0.3);
    }

    public List<Integer> getAvailableAlertThresholds() {
        return config.getIntegerList("alerts.available-thresholds");
    }

    public List<Integer> getDefaultEnabledAlerts() {
        return config.getIntegerList("alerts.default-enabled");
    }

    public String getAlertSound() {
        return config.getString("alerts.alert-sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
    }

    public float getAlertSoundVolume() {
        return (float) config.getDouble("alerts.alert-sound-volume", 1.0);
    }

    public float getAlertSoundPitch() {
        return (float) config.getDouble("alerts.alert-sound-pitch", 1.0);
    }

    public int getParticleSpawnInterval() {
        return config.getInt("particles.spawn-interval", 5);
    }

    public int getParticlesPerSpawn() {
        return config.getInt("particles.particles-per-spawn", 3);
    }

    public long getPlaceholderMaxFlySeconds() {
        return config.getLong("placeholders.max-fly-time-seconds", 86400);
    }

    public int getPlaceholderBarLength() {
        return Math.max(1, config.getInt("placeholders.bar.length", 10));
    }

    public String getPlaceholderBarFilled() {
        return config.getString("placeholders.bar.filled", "■");
    }

    public String getPlaceholderBarEmpty() {
        return config.getString("placeholders.bar.empty", "□");
    }

    public boolean isUpdateCheckerEnabled() {
        return config.getBoolean("update-checker.enabled", true);
    }

    public boolean isUpdateNotifyOnJoin() {
        return config.getBoolean("update-checker.notify-on-join", true);
    }
}
