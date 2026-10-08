/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease;

import com.joshlucem.skylease.commands.FlyAdminCommand;
import com.joshlucem.skylease.commands.FlyCommand;
import com.joshlucem.skylease.commands.SkyleaseCommand;
import com.joshlucem.skylease.database.DatabaseManager;
import com.joshlucem.skylease.listeners.ConnectionListener;
import com.joshlucem.skylease.listeners.FlightListener;
import com.joshlucem.skylease.listeners.MenuListener;
import com.joshlucem.skylease.managers.AlertManager;
import com.joshlucem.skylease.managers.ConfigManager;
import com.joshlucem.skylease.managers.FlightManager;
import com.joshlucem.skylease.managers.MenuManager;
import com.joshlucem.skylease.managers.MessageManager;
import com.joshlucem.skylease.managers.ParticleManager;
import com.joshlucem.skylease.managers.PlaceholderAPIManager;
import com.joshlucem.skylease.managers.WorldGuardManager;
import com.joshlucem.skylease.utils.ConfigUpdater;
import com.joshlucem.skylease.utils.Credits;
import com.joshlucem.skylease.utils.PluginLogger;
import com.joshlucem.skylease.utils.UpdateChecker;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Start-up order matters: config, then the database, then flight. */
public class Skylease extends JavaPlugin {

    private PluginLogger pluginLogger;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private DatabaseManager databaseManager;
    private FlightManager flightManager;
    private ParticleManager particleManager;
    private AlertManager alertManager;
    private MenuManager menuManager;
    private WorldGuardManager worldGuardManager;
    private PlaceholderAPIManager placeholderAPIManager;

    @Override
    public void onLoad() {
        // the flag must exist before WorldGuard finishes enabling
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                WorldGuardManager.registerFlag(this);
            } catch (LinkageError | RuntimeException e) {
                getLogger().warning("Could not register the WorldGuard flag: " + e);
            }
        }
    }

    @Override
    public void onEnable() {
        pluginLogger = new PluginLogger(this);
        pluginLogger.banner();

        try {
            pluginLogger.section("Configuration");
            ConfigUpdater.prepare(this);
            configManager = new ConfigManager(this);
            pluginLogger.ok("config.yml loaded");
            messageManager = new MessageManager(this);
            pluginLogger.ok("Language loaded: " + configManager.getLanguage());

            pluginLogger.section("Database");
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();
            pluginLogger.ok("Connected (" + configManager.getDatabaseType().toUpperCase() + ")");

            pluginLogger.section("Systems");
            flightManager = new FlightManager(this);
            alertManager = new AlertManager(this);
            particleManager = new ParticleManager(this);
            menuManager = new MenuManager(this);
            pluginLogger.ok("Flight, alerts, particles (" + particleManager.getAllEffects().size() + " effects) and menus");

            pluginLogger.section("Integrations");
            worldGuardManager = new WorldGuardManager(this);
            placeholderAPIManager = new PlaceholderAPIManager(this);
            placeholderAPIManager.initialize();

            registerCommands();
            registerListeners();
            flightManager.startFlightTask();
            particleManager.startParticleTask();

            new UpdateChecker(this, "joshlucem", "Skylease").checkForUpdates();

            pluginLogger.section("Ready");
            pluginLogger.ok("Skylease " + Credits.versionLabel() + " enabled");
        } catch (Exception e) {
            pluginLogger.error("Could not start the plugin: " + e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (flightManager != null) {
            flightManager.stopTasks();
            flightManager.disableAllFlights();
        }
        if (particleManager != null) {
            particleManager.stopParticleTask();
        }
        if (placeholderAPIManager != null) {
            placeholderAPIManager.unregister();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (pluginLogger != null) {
            pluginLogger.shutdownBanner();
        }
    }

    private void registerCommands() {
        FlyCommand fly = new FlyCommand(this);
        bind("fly", fly, fly);

        FlyAdminCommand admin = new FlyAdminCommand(this);
        bind("flyadmin", admin, admin);

        SkyleaseCommand about = new SkyleaseCommand();
        bind("skylease", about, about);
    }

    private void bind(String name, org.bukkit.command.CommandExecutor executor, org.bukkit.command.TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException("The command /" + name + " is not declared in plugin.yml");
        }
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new FlightListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);
    }

    public void reloadConfigs() {
        configManager.reload();
        messageManager.reload();
        menuManager.reload();
        particleManager.reload();
        flightManager.restartTask();
        particleManager.restartTask();
    }

    public PluginLogger log() {
        return pluginLogger;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public FlightManager getFlightManager() {
        return flightManager;
    }

    public ParticleManager getParticleManager() {
        return particleManager;
    }

    public AlertManager getAlertManager() {
        return alertManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public WorldGuardManager getWorldGuardManager() {
        return worldGuardManager;
    }

}
