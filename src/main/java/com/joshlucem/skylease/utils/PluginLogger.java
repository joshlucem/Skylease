/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.utils;

import com.joshlucem.skylease.Skylease;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

import java.text.Normalizer;
import java.util.Map;

/** ASCII console output in colour. Everything goes through ascii() so it looks the same in any terminal. */
public class PluginLogger {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final int WIDTH = 62;

    // 5x5 letters, only the ones in the name
    private static final Map<Character, String[]> FONT = Map.of(
        'S', new String[]{" ####", "#    ", " ### ", "    #", "#### "},
        'K', new String[]{"#   #", "#  # ", "###  ", "#  # ", "#   #"},
        'Y', new String[]{"#   #", " # # ", "  #  ", "  #  ", "  #  "},
        'L', new String[]{"#    ", "#    ", "#    ", "#    ", "#####"},
        'E', new String[]{"#####", "#    ", "#### ", "#    ", "#####"},
        'A', new String[]{" ### ", "#   #", "#####", "#   #", "#   #"}
    );

    private final ComponentLogger logger;
    private volatile boolean debug;

    public PluginLogger(Skylease plugin) {
        this.logger = plugin.getComponentLogger();
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean isDebug() {
        return debug;
    }

    public void banner() {
        String rule = "+" + "-".repeat(WIDTH - 2) + "+";
        String[] logo = logoLines(Credits.NAME.toUpperCase());

        line(MINI.deserialize("<dark_gray>" + rule));
        for (String row : logo) {
            line(MINI.deserialize("<dark_gray>|</dark_gray> <gradient:aqua:blue>" + pad(row) + "</gradient> <dark_gray>|"));
        }
        line(MINI.deserialize("<dark_gray>|" + " ".repeat(WIDTH - 2) + "|"));
        line(boxed("<gray>Per-player flight time", "Per-player flight time"));
        String version = "Version " + Credits.versionLabel();
        line(boxed("<gray>" + version, version));
        String author = "Created by " + Credits.AUTHOR + " - " + Credits.URL;
        line(boxed("<gray>Created by <aqua>" + Credits.AUTHOR + "</aqua> - <blue>" + Credits.URL, author));
        line(MINI.deserialize("<dark_gray>" + rule));
    }

    public void shutdownBanner() {
        line(MINI.deserialize("<dark_gray>[ <aqua>" + Credits.NAME + "</aqua> <dark_gray>] <gray>Plugin disabled. See you soon."));
    }

    public void section(String title) {
        line(Component.empty());
        line(Component.text("== ", NamedTextColor.DARK_GRAY)
            .append(Component.text(ascii(title).toUpperCase(), NamedTextColor.AQUA))
            .append(Component.text(" ==", NamedTextColor.DARK_GRAY)));
    }

    public void ok(String message) {
        tagged("OK", NamedTextColor.GREEN, message);
    }

    public void info(String message) {
        tagged("INFO", NamedTextColor.AQUA, message);
    }

    public void warn(String message) {
        tagged("WARN", NamedTextColor.YELLOW, message);
    }

    public void error(String message) {
        tagged("FAIL", NamedTextColor.RED, message);
    }

    public void error(String message, Throwable error) {
        tagged("FAIL", NamedTextColor.RED, message);
        logger.error("Detalle del error", error);
    }

    public void debug(String message) {
        if (debug) {
            tagged("DBG", NamedTextColor.LIGHT_PURPLE, message);
        }
    }

    private void tagged(String tag, NamedTextColor color, String message) {
        // fixed-width tag so messages line up
        String padded = String.format("%-4s", tag);
        line(Component.text("[", NamedTextColor.DARK_GRAY)
            .append(Component.text(padded, color))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(Component.text(ascii(message), NamedTextColor.WHITE)));
    }

    static String ascii(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder result = new StringBuilder(plain.length());
        for (char c : plain.toCharArray()) {
            if (c == '\u00bf' || c == '\u00a1') {
                continue;
            }
            result.append(c < 128 ? c : '?');
        }
        return result.toString();
    }

    private void line(Component component) {
        logger.info(component);
    }

    private Component boxed(String miniMessage, String plainText) {
        int free = WIDTH - 4 - plainText.length();
        int left = Math.max(0, free / 2);
        int right = Math.max(0, free - left);
        return MINI.deserialize("<dark_gray>|</dark_gray> " + " ".repeat(left) + miniMessage + "<reset>"
            + " ".repeat(right) + " <dark_gray>|");
    }

    private String pad(String row) {
        int free = WIDTH - 4 - row.length();
        int left = Math.max(0, free / 2);
        return " ".repeat(left) + row + " ".repeat(Math.max(0, free - left));
    }

    private String[] logoLines(String word) {
        String[] rows = new String[5];
        for (int r = 0; r < rows.length; r++) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < word.length(); i++) {
                String[] glyph = FONT.get(word.charAt(i));
                if (glyph == null) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append("  ");
                }
                builder.append(glyph[r]);
            }
            rows[r] = builder.toString();
        }
        return rows;
    }
}
