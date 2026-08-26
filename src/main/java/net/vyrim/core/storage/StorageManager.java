package net.vyrim.core.storage;

import net.vyrim.core.VyrimCore;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Owns the single SQLite connection for the whole plugin.
 * Any module needing persistence should go through this instead of
 * opening its own database file.
 */
public final class StorageManager {

    private final VyrimCore core;
    private Connection connection;

    public StorageManager(VyrimCore core) {
        this.core = core;
    }

    public void connect() {
        if (!core.getDataFolder().exists()) {
            core.getDataFolder().mkdirs();
        }
        File dbFile = new File(core.getDataFolder(), "vyrimcore.db");
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            core.getLogger().info("[Storage] Connected to SQLite database at " + dbFile.getName());
        } catch (ClassNotFoundException | SQLException ex) {
            core.getLogger().severe("[Storage] Failed to connect to SQLite: " + ex.getMessage());
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException ex) {
            return false;
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
                core.getLogger().info("[Storage] Closed SQLite connection.");
            } catch (SQLException ex) {
                core.getLogger().warning("[Storage] Failed to close connection: " + ex.getMessage());
            }
        }
    }
}