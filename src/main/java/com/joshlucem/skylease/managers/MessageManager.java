/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.managers;

import com.joshlucem.skylease.Skylease;
import com.joshlucem.skylease.utils.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Text from lang/&lt;language&gt;.yml, as MiniMessage. An empty message is not sent.
 * Keys missing from the chosen file fall back to the bundled English one.
 * text() escapes a value; mini() interprets it, so only use it for our own content.
 */
public class MessageManager {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final String[] PREFIXES = {"prefix", "error-prefix", "success-prefix", "warning-prefix"};
    private static final String DEFAULT_LANGUAGE = "en";

    private final Skylease plugin;
    private YamlConfiguration selected;
    private YamlConfiguration fallback;

    public MessageManager(Skylease plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        fallback = bundled(DEFAULT_LANGUAGE);

        String language = plugin.getConfigManager().getLanguage();
        File file = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        if (file.isFile()) {
            selected = YamlConfiguration.loadConfiguration(file);
        } else {
            plugin.log().warn("Language file lang/" + language + ".yml not found, using English");
            selected = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "lang/" + DEFAULT_LANGUAGE + ".yml"));
        }
    }

    private YamlConfiguration bundled(String language) {
        try (Reader reader = new InputStreamReader(plugin.getResource("lang/" + language + ".yml"), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            return new YamlConfiguration();
        }
    }

    /** A literal value: any MiniMessage tag inside it is shown as text. */
    public static TagResolver text(String key, Object value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }

    /** A value with MiniMessage formatting. Do not use it with text typed by players. */
    public static TagResolver mini(String key, String value) {
        return Placeholder.parsed(key, value);
    }

    public Component parse(String miniMessage, TagResolver... resolvers) {
        return MINI.deserialize(miniMessage, withPrefixes(resolvers));
    }

    /** Raw string for a key. Empty if it is set to "" on purpose; the English one if it is missing. */
    public String raw(String path) {
        if (selected.contains(path)) {
            return selected.getString(path, "");
        }
        return fallback.getString(path, "");
    }

    public List<String> rawList(String path) {
        return selected.contains(path) ? selected.getStringList(path) : fallback.getStringList(path);
    }

    public Component get(String path, TagResolver... resolvers) {
        String raw = raw(path);
        return raw.isBlank() ? Component.empty() : MINI.deserialize(raw, withPrefixes(resolvers));
    }

    public List<Component> getList(String path, TagResolver... resolvers) {
        List<Component> lines = new ArrayList<>();
        for (String raw : rawList(path)) {
            lines.add(raw == null || raw.isBlank() ? Component.empty() : MINI.deserialize(raw, withPrefixes(resolvers)));
        }
        return lines;
    }

    public void send(CommandSender target, String path, TagResolver... resolvers) {
        if (!raw(path).isBlank()) {
            target.sendMessage(get(path, resolvers));
        }
    }

    public void sendList(CommandSender target, String path, TagResolver... resolvers) {
        getList(path, resolvers).forEach(target::sendMessage);
    }

    /**
     * Time ready to show. Players with unlimited flight see the time.unlimited symbol; everyone else
     * sees the real time, including "0s" once it has run out. An offline player has no permissions to
     * check, so they get the real time.
     */
    public String displayTime(OfflinePlayer target, long seconds) {
        if (target instanceof Player player && player.hasPermission("skylease.unlimited")) {
            return raw("time.unlimited");
        }
        return TimeUtil.formatTime(seconds);
    }

    private TagResolver[] withPrefixes(TagResolver... extra) {
        List<TagResolver> all = new ArrayList<>();
        for (String prefix : PREFIXES) {
            all.add(Placeholder.parsed(prefix, raw(prefix)));
        }
        all.addAll(List.of(extra));
        return all.toArray(new TagResolver[0]);
    }
}
