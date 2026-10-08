/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.database;

import com.joshlucem.skylease.Skylease;
import com.joshlucem.skylease.managers.ConfigManager;
import com.joshlucem.skylease.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.LongUnaryOperator;

/**
 * SQLite or MySQL. Everything goes through a single thread, so the connection is not shared
 * and writes happen in order. Online players live in the cache from the pre-login on.
 */
public class DatabaseManager {

    private final Skylease plugin;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Skylease-DB");
        thread.setDaemon(true);
        return thread;
    });

    // only used from the Skylease-DB thread
    private Connection connection;
    private boolean mysql;

    // alerts as last written, to rewrite them only when they change (Skylease-DB thread only)
    private final Map<UUID, Set<Integer>> savedAlerts = new HashMap<>();
    private long lastUsed;

    private BukkitTask flushTask;
    private BukkitTask purgeTask;

    public DatabaseManager(Skylease plugin) {
        this.plugin = plugin;
    }

    public void initialize() throws SQLException {
        try {
            executor.submit(() -> {
                connect();
                createTables();
                return null;
            }).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new SQLException("Could not initialise the database: " + rootMessage(e), e);
        }

        ConfigManager config = plugin.getConfigManager();
        long flushTicks = Math.max(5, config.getSaveIntervalSeconds()) * 20L;
        flushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::flushDirty, flushTicks, flushTicks);

        if (config.isDatabaseAutoPurgeEnabled()) {
            if (config.isDatabasePurgeOnStartup()) {
                purgeInactive();
            }
            long purgeTicks = Math.max(1, config.getDatabasePurgeIntervalHours()) * 60L * 60L * 20L;
            purgeTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::purgeInactive, purgeTicks, purgeTicks);
        }
    }

    private void connect() throws SQLException {
        closeQuietly();
        ConfigManager config = plugin.getConfigManager();
        String type = config.getDatabaseType();
        mysql = type.equalsIgnoreCase("mysql") || type.equalsIgnoreCase("mariadb");
        loadDriver(mysql ? "com.mysql.cj.jdbc.Driver" : "org.sqlite.JDBC");

        if (mysql) {
            String url = "jdbc:mysql://" + config.getDatabaseHost() + ":" + config.getDatabasePort() + "/"
                + config.getDatabaseName() + "?useSSL=" + config.isDatabaseUseSSL()
                + "&useUnicode=true&characterEncoding=UTF-8&connectTimeout=10000";
            connection = DriverManager.getConnection(url, config.getDatabaseUsername(), config.getDatabasePassword());
            plugin.log().info("Database: MySQL at " + config.getDatabaseHost() + ":" + config.getDatabasePort());
        } else {
            File folder = new File(plugin.getDataFolder(), "data");
            if (!folder.exists() && !folder.mkdirs()) {
                throw new SQLException("Could not create the folder " + folder);
            }
            File file = new File(folder, "players.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            plugin.log().info("Database: SQLite in " + file.getName());
        }
    }

    // a connection that has not been used for a while may have been closed by the server, so check it then
    private Connection connection() throws SQLException {
        long now = System.currentTimeMillis();
        boolean idle = now - lastUsed > 30_000;
        if (connection == null || connection.isClosed() || (idle && !connection.isValid(2))) {
            connect();
        }
        lastUsed = now;
        return connection;
    }

    // the drivers come from plugin.yml libraries, which DriverManager's own scan may not see
    private void loadDriver(String className) throws SQLException {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC driver not found: " + className, e);
        }
    }

    private void closeQuietly() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException ignored) {
            // it was already broken
        }
        connection = null;
    }

    private void createTables() throws SQLException {
        try (Statement statement = connection().createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    username VARCHAR(32) NOT NULL,
                    fly_time BIGINT NOT NULL DEFAULT 0,
                    is_flying SMALLINT NOT NULL DEFAULT 0,
                    particle_effect VARCHAR(64) NOT NULL DEFAULT 'none',
                    last_seen BIGINT NOT NULL
                )""");
            statement.execute("""
                CREATE TABLE IF NOT EXISTS alerts (
                    uuid VARCHAR(36) NOT NULL,
                    threshold INT NOT NULL,
                    PRIMARY KEY (uuid, threshold)
                )""");
        }
        try (Statement statement = connection().createStatement()) {
            // MySQL has no IF NOT EXISTS for CREATE INDEX
            statement.execute("CREATE INDEX idx_players_last_seen ON players (last_seen)");
        } catch (SQLException ignored) {
            // the index already exists
        }
    }

    public PlayerData getCachedPlayerData(UUID uuid) {
        return cache.get(uuid);
    }

    // called from the pre-login event, which already runs off the main thread
    public PlayerData loadAndCache(UUID uuid, String name) {
        // a second connection with the same UUID must not replace the live data
        PlayerData existing = cache.get(uuid);
        if (existing != null) {
            return existing;
        }
        try {
            PlayerData data = executor.submit(() -> read(uuid, name)).get(15, TimeUnit.SECONDS);
            cache.put(uuid, data);
            return data;
        } catch (Exception e) {
            plugin.log().error("Could not load the data of " + name + ": " + rootMessage(e));
            return null;
        }
    }

    public CompletableFuture<PlayerData> getPlayerDataAsync(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return read(uuid, null);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }, executor);
    }

    // one retry: if the server dropped the connection while idle, the first query finds out and the second reconnects
    private PlayerData read(UUID uuid, String nameHint) throws SQLException {
        try {
            return readOnce(uuid, nameHint);
        } catch (SQLException first) {
            lastUsed = 0;
            return readOnce(uuid, nameHint);
        }
    }

    private PlayerData readOnce(UUID uuid, String nameHint) throws SQLException {
        PlayerData data = new PlayerData(uuid);

        try (PreparedStatement statement = connection().prepareStatement(
            "SELECT username, fly_time, is_flying, particle_effect, last_seen FROM players WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet row = statement.executeQuery()) {
                if (row.next()) {
                    data.setUsername(row.getString("username"));
                    data.setFlyTime(row.getLong("fly_time"));
                    data.setFlying(row.getInt("is_flying") == 1);
                    data.setParticleEffect(row.getString("particle_effect"));
                    data.setLastSeen(row.getLong("last_seen"));

                    try (PreparedStatement alerts = connection().prepareStatement(
                        "SELECT threshold FROM alerts WHERE uuid = ?")) {
                        alerts.setString(1, uuid.toString());
                        try (ResultSet rows = alerts.executeQuery()) {
                            while (rows.next()) {
                                data.getEnabledAlerts().add(rows.getInt("threshold"));
                            }
                        }
                    }
                    return data;
                }
            }
        }

        // no record yet: starting values, written on the first save
        data.setUsername(nameHint != null ? nameHint : Bukkit.getOfflinePlayer(uuid).getName());
        data.getEnabledAlerts().addAll(plugin.getConfigManager().getDefaultEnabledAlerts());
        return data;
    }

    public void markDirty(UUID uuid) {
        dirty.add(uuid);
    }

    public CompletableFuture<Void> saveAsync(PlayerData data) {
        dirty.remove(data.getUuid());
        return CompletableFuture.runAsync(() -> writeWithRetry(data.copy()), executor);
    }

    // for logins that loaded but never joined
    public void discard(UUID uuid) {
        cache.remove(uuid);
        dirty.remove(uuid);
    }

    public void saveAndEvict(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        dirty.remove(uuid);
        if (data != null) {
            executor.execute(() -> {
                writeWithRetry(data.copy());
                savedAlerts.remove(uuid);
            });
        }
    }

    public CompletableFuture<Long> updateFlyTime(UUID uuid, LongUnaryOperator operation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                PlayerData data = cache.get(uuid);
                if (data == null) {
                    data = read(uuid, null);
                }
                data.updateFlyTime(operation);
                if (!writeWithRetry(data.copy())) {
                    throw new SQLException("the new time could not be saved");
                }
                if (!cache.containsKey(uuid)) {
                    savedAlerts.remove(uuid);
                }
                return data.getFlyTime();
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }, executor);
    }

    private void flushDirty() {
        for (UUID uuid : Set.copyOf(dirty)) {
            PlayerData data = cache.get(uuid);
            dirty.remove(uuid);
            if (data != null) {
                // the copy is taken on the database thread, after any queued change has been applied
                executor.execute(() -> {
                    if (!writeWithRetry(data.copy())) {
                        dirty.add(uuid); // try again on the next flush
                    }
                });
            }
        }
    }

    // a second attempt covers a connection that went stale; it must not sleep, the thread is shared
    private boolean writeWithRetry(PlayerData data) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            if (write(data)) {
                return true;
            }
            lastUsed = 0; // check the connection before the next try
        }
        plugin.log().error("Gave up saving the data of " + data.getUsername());
        return false;
    }

    private boolean write(PlayerData data) {
        String upsert = mysql
            ? """
              INSERT INTO players (uuid, username, fly_time, is_flying, particle_effect, last_seen)
              VALUES (?, ?, ?, ?, ?, ?)
              ON DUPLICATE KEY UPDATE username = VALUES(username), fly_time = VALUES(fly_time),
                  is_flying = VALUES(is_flying), particle_effect = VALUES(particle_effect),
                  last_seen = VALUES(last_seen)"""
            : """
              INSERT INTO players (uuid, username, fly_time, is_flying, particle_effect, last_seen)
              VALUES (?, ?, ?, ?, ?, ?)
              ON CONFLICT(uuid) DO UPDATE SET username = excluded.username, fly_time = excluded.fly_time,
                  is_flying = excluded.is_flying, particle_effect = excluded.particle_effect,
                  last_seen = excluded.last_seen""";

        UUID uuid = data.getUuid();
        Set<Integer> alerts = Set.copyOf(data.getEnabledAlerts());
        boolean alertsChanged = !alerts.equals(savedAlerts.get(uuid));

        try {
            Connection conn = connection();
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement statement = conn.prepareStatement(upsert)) {
                    statement.setString(1, uuid.toString());
                    statement.setString(2, data.getUsername());
                    statement.setLong(3, data.getFlyTime());
                    statement.setInt(4, data.isFlying() ? 1 : 0);
                    statement.setString(5, data.getParticleEffect());
                    statement.setLong(6, data.getLastSeen());
                    statement.executeUpdate();
                }
                if (alertsChanged) {
                    try (PreparedStatement delete = conn.prepareStatement("DELETE FROM alerts WHERE uuid = ?")) {
                        delete.setString(1, uuid.toString());
                        delete.executeUpdate();
                    }
                    try (PreparedStatement insert = conn.prepareStatement("INSERT INTO alerts (uuid, threshold) VALUES (?, ?)")) {
                        for (int threshold : alerts) {
                            insert.setString(1, uuid.toString());
                            insert.setInt(2, threshold);
                            insert.addBatch();
                        }
                        insert.executeBatch();
                    }
                }
                conn.commit();
                savedAlerts.put(uuid, alerts);
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.log().warn("Could not save the data of " + data.getUsername() + ": " + e.getMessage());
            return false;
        }
    }

    private void purgeInactive() {
        int days = plugin.getConfigManager().getDatabasePurgeDaysInactive();
        if (days <= 0) {
            return;
        }
        long cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L;
        // players who still have time are kept unless the config says otherwise
        String keep = plugin.getConfigManager().isPurgeKeepingPlayersWithTime() ? " AND fly_time <= 0" : "";
        executor.execute(() -> {
            try {
                try (PreparedStatement alerts = connection().prepareStatement(
                    "DELETE FROM alerts WHERE uuid IN (SELECT uuid FROM players WHERE last_seen < ?" + keep + ")")) {
                    alerts.setLong(1, cutoff);
                    alerts.executeUpdate();
                }
                try (PreparedStatement players = connection().prepareStatement("DELETE FROM players WHERE last_seen < ?" + keep)) {
                    players.setLong(1, cutoff);
                    int removed = players.executeUpdate();
                    if (removed > 0) {
                        plugin.log().info("Removed " + removed + " inactive players from the database.");
                    }
                }
            } catch (SQLException e) {
                plugin.log().warn("Could not purge the database: " + e.getMessage());
            }
        });
    }

    public void close() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        if (purgeTask != null) {
            purgeTask.cancel();
        }

        for (PlayerData data : cache.values()) {
            executor.execute(() -> writeWithRetry(data.copy()));
        }
        cache.clear();
        dirty.clear();

        executor.execute(() -> {
            try {
                if (connection != null) {
                    connection.close();
                }
            } catch (SQLException e) {
                plugin.log().warn("Error closing the database: " + e.getMessage());
            }
        });
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                plugin.log().warn("The database took too long to close; some data may be unsaved.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
