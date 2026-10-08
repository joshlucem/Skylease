/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.utils;

import com.joshlucem.skylease.Skylease;
import com.joshlucem.skylease.managers.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Looks at the latest GitHub release: one GET on start-up, nothing about the server is sent. */
public class UpdateChecker implements Listener {

    private static final Pattern TAG = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern URL = Pattern.compile("\"html_url\"\\s*:\\s*\"([^\"]+)\"");
    // 1.0.0, v1.0.0-build2, "1.0.0 Build 2": major.minor.patch with an optional build number
    private static final Pattern VERSION = Pattern.compile("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)(?:[ _.\\-]*[bB]uild[ _.\\-]*(\\d+))?.*$");

    private final Skylease plugin;
    private final String owner;
    private final String repository;

    private volatile String latestLabel;
    private volatile String downloadUrl;
    private volatile boolean updateAvailable;

    public UpdateChecker(Skylease plugin, String owner, String repository) {
        this.plugin = plugin;
        this.owner = owner;
        this.repository = repository;
    }

    public void checkForUpdates() {
        if (!plugin.getConfigManager().isUpdateCheckerEnabled()) {
            return;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(
                "https://api.github.com/repos/" + owner + "/" + repository + "/releases/latest"))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Skylease-UpdateChecker")
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();

        HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept(this::handle)
            .exceptionally(error -> {
                plugin.log().debug("Update check failed: " + error.getMessage());
                return null;
            });
    }

    private void handle(HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            plugin.log().debug("Update check: GitHub answered " + response.statusCode());
            return;
        }
        Matcher tag = TAG.matcher(response.body());
        Matcher url = URL.matcher(response.body());
        if (!tag.find() || !url.find()) {
            return;
        }

        int[] latest = parse(tag.group(1));
        int[] current = parse(Credits.version() + "-build" + Credits.build());
        if (latest == null || current == null) {
            return;
        }

        downloadUrl = url.group(1);
        latestLabel = latest[0] + "." + latest[1] + "." + latest[2] + " - Build " + latest[3];
        updateAvailable = isNewer(latest, current);

        if (updateAvailable) {
            plugin.log().warn("A new version is out: " + latestLabel + " (you have " + Credits.versionLabel() + "). " + downloadUrl);
            Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getPluginManager().registerEvents(this, plugin));
        } else {
            plugin.log().ok("Skylease is up to date");
        }
    }

    /** {major, minor, patch, build}, or null if the text is not a version. A missing build counts as 0. */
    static int[] parse(String text) {
        Matcher matcher = VERSION.matcher(text.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return new int[]{
                Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)),
                matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4))
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A higher major, minor or patch wins; with the same version, the higher build wins. */
    static boolean isNewer(int[] candidate, int[] current) {
        for (int i = 0; i < 4; i++) {
            if (candidate[i] != current[i]) {
                return candidate[i] > current[i];
            }
        }
        return false;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!updateAvailable || !plugin.getConfigManager().isUpdateNotifyOnJoin() || !player.hasPermission("skylease.admin.updates")) {
            return;
        }
        // two seconds, so it does not get lost among the join messages
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getMessageManager().sendList(player, "update.available",
                    MessageManager.text("current", Credits.versionLabel()),
                    MessageManager.text("latest", latestLabel),
                    MessageManager.mini("url", downloadUrl));
            }
        }, 40L);
    }
}
