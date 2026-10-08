/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.commands;

import com.joshlucem.skylease.utils.Credits;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/** /skylease about. The text is fixed in code because the licence asks that credits cannot be hidden by config. */
public class SkyleaseCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && !args[0].equalsIgnoreCase("about")) {
            sender.sendMessage(MINI.deserialize("<red>Usage: <yellow>/skylease about"));
            return true;
        }

        sender.sendMessage(MINI.deserialize("<dark_gray>----------------------------------------"));
        sender.sendMessage(MINI.deserialize("<gradient:aqua:blue><bold>" + Credits.NAME + "</bold></gradient> <gray>"
            + Credits.versionLabel()));
        sender.sendMessage(MINI.deserialize("<gray>Created by <aqua><author></aqua>",
            Placeholder.unparsed("author", Credits.AUTHOR)));
        sender.sendMessage(Component.text(Credits.URL, NamedTextColor.BLUE, TextDecoration.UNDERLINED)
            .clickEvent(ClickEvent.openUrl(Credits.URL))
            .hoverEvent(HoverEvent.showText(Component.text("Open the project"))));
        sender.sendMessage(MINI.deserialize("<gray>Licence: <white><license>",
            Placeholder.unparsed("license", Credits.LICENSE)));
        sender.sendMessage(MINI.deserialize("<dark_gray>If you redistribute it, please credit the author."));
        sender.sendMessage(MINI.deserialize("<dark_gray>----------------------------------------"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 && "about".startsWith(args[0].toLowerCase()) ? List.of("about") : List.of();
    }
}
