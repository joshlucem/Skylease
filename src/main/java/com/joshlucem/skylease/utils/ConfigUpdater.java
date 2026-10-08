/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.utils;

import com.joshlucem.skylease.Skylease;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Creates missing files and adds new options to existing ones without touching the user's. Backs up to backups/ first. */
public final class ConfigUpdater {

    private static final String VERSION_KEY = "config-version";

    private ConfigUpdater() {
    }

    public static void prepare(Skylease plugin) {
        update(plugin, "config.yml", true);
        update(plugin, "menus.yml", true);
        // effects.yml is built by the user: effects they deleted are not added back
        update(plugin, "effects.yml", false);
        update(plugin, "lang/en.yml", true);
        update(plugin, "lang/es.yml", true);
    }

    private static void update(Skylease plugin, String fileName, boolean mergeMissingKeys) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
            plugin.log().info("Created " + fileName);
            return;
        }

        try (Reader reader = new InputStreamReader(plugin.getResource(fileName), StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            YamlConfiguration user = YamlConfiguration.loadConfiguration(file);

            int shipped = defaults.getInt(VERSION_KEY, 1);
            int current = user.getInt(VERSION_KEY, 0);
            if (current >= shipped) {
                return;
            }

            backup(plugin, file);
            if (mergeMissingKeys) {
                int added = 0;
                for (String key : defaults.getKeys(true)) {
                    if (!user.contains(key) && !defaults.isConfigurationSection(key)) {
                        user.set(key, defaults.get(key));
                        added++;
                    }
                }
                plugin.log().info(fileName + ": added " + added + " new options");
            }
            user.set(VERSION_KEY, shipped);
            user.save(file);
        } catch (IOException | NullPointerException e) {
            plugin.log().error("Could not update " + fileName + ": " + e.getMessage());
        }
    }

    private static void backup(Skylease plugin, File file) throws IOException {
        File folder = new File(plugin.getDataFolder(), "backups");
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Could not create " + folder);
        }
        File copy = new File(folder, file.getName() + "." + System.currentTimeMillis() + ".bak");
        Files.copy(file.toPath(), copy.toPath());
        plugin.log().info("Backup: backups/" + copy.getName());
    }
}
