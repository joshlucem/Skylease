/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.managers;

import com.joshlucem.skylease.Skylease;
import com.joshlucem.skylease.placeholders.SkyleasePlaceholder;
import org.bukkit.Bukkit;

public class PlaceholderAPIManager {

    private final Skylease plugin;
    private SkyleasePlaceholder expansion;
    private boolean enabled;

    public PlaceholderAPIManager(Skylease plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.log().info("PlaceholderAPI: not installed, placeholders unavailable");
            return;
        }

        expansion = new SkyleasePlaceholder(plugin);
        enabled = expansion.register();
        if (enabled) {
            plugin.log().ok("PlaceholderAPI: %skylease_...% placeholders registered");
        } else {
            plugin.log().warn("PlaceholderAPI: could not register the expansion");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void unregister() {
        if (expansion != null) {
            expansion.unregister();
        }
    }
}
