package sandtechnology.redpacket.database;

import org.bukkit.Bukkit;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import static sandtechnology.redpacket.RedPacketPlugin.config;
import static sandtechnology.redpacket.RedPacketPlugin.getInstance;
import static sandtechnology.redpacket.RedPacketPlugin.log;

public class SqliteManager extends AbstractDatabaseManager {

    private final Path databasePath;
    private final Path backupDir;
    private static final String SQLITE_CONFIG = "?journal_mode=WAL&synchronous=NORMAL&busy_timeout=5000";

    public SqliteManager(String tableName) {
        String fileName = config().getString("Database.FileName", "redpacket.db");
        this.databasePath = getInstance().getDataFolder().toPath().resolve(fileName);
        this.backupDir = getInstance().getDataFolder().toPath().resolve("backups");
        setup(tableName);
    }

    @Override
    void setup(String tableName) {
        try {
            Class.forName("org.sqlite.JDBC");
            this.tableName = tableName;
            Files.createDirectories(databasePath.getParent());
            String url = "jdbc:sqlite:" + databasePath + SQLITE_CONFIG;
            connection = DriverManager.getConnection(url);
            setupConnectionParameters();
            createTable();
            createIndex();
            connection.setAutoCommit(false);
            startCommitTimer();
            setRunning(true);
            log(Level.INFO, "SQLite 数据库初始化完成: " + databasePath);
            log(Level.INFO, "数据库文件大小: " + (new File(databasePath.toString()).exists() ?
                    Files.size(databasePath) / 1024 + " KB" : "不存在"));
        } catch (Exception ex) {
            log(Level.SEVERE, "SQLite 数据库初始化出现错误: " + ex.getMessage());
            throw new RuntimeException("数据库初始化出现错误，将关闭本插件！", ex);
        }
    }

    private void setupConnectionParameters() throws SQLException {
        if (connection != null) {
            connection.createStatement().execute("PRAGMA foreign_keys = ON");
            connection.createStatement().execute("PRAGMA cache_size = -2000");
            connection.createStatement().execute("PRAGMA page_size = 4096");
            connection.createStatement().execute("PRAGMA temp_store = MEMORY");
        }
    }

    private void createTable() {
        String createTableSQL =
                "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                        "UUID TEXT PRIMARY KEY," +
                        "playerUUID TEXT NOT NULL," +
                        "giveType TEXT NOT NULL," +
                        "RedPacketType TEXT NOT NULL," +
                        "amount INTEGER NOT NULL," +
                        "money REAL NOT NULL," +
                        "moneyMap TEXT NOT NULL," +
                        "extraData TEXT NOT NULL," +
                        "givers TEXT NOT NULL," +
                        "expireTime INTEGER NOT NULL," +
                        "timeZone TEXT NOT NULL," +
                        "expired INTEGER NOT NULL)";
        executeUpdate(createTableSQL);
    }

    private void createIndex() {
        try {
            String createIndexSQL =
                    "CREATE INDEX IF NOT EXISTS searchIndex ON " + tableName + " (playerUUID, expireTime)";
            executeUpdate(createIndexSQL);
            String expiredIndexSQL =
                    "CREATE INDEX IF NOT EXISTS expiredIndex ON " + tableName + " (expired, expireTime)";
            executeUpdate(expiredIndexSQL);
            String typeIndexSQL =
                    "CREATE INDEX IF NOT EXISTS typeIndex ON " + tableName + " (RedPacketType, expired)";
            executeUpdate(typeIndexSQL);
        } catch (Exception e) {
            log(Level.WARNING, "创建索引失败，可能已存在: " + e.getMessage());
        }
    }

    public void setupAsync(String tableName, Runnable callback) {
        Bukkit.getScheduler().runTaskAsynchronously(getInstance(), () -> {
            try {
                setup(tableName);
                if (callback != null) {
                    Bukkit.getScheduler().runTask(getInstance(), callback);
                }
            } catch (Exception e) {
                log(Level.SEVERE, "异步初始化数据库失败: " + e.getMessage());
                if (callback != null) {
                    Bukkit.getScheduler().runTask(getInstance(), callback);
                }
            }
        });
    }

    @Override
    synchronized public void setRunning(boolean running) {
        this.running = running;
        try {
            if (connection != null && !connection.isClosed()) {
                commit();
            }
        } catch (Exception e) {
            log(Level.WARNING, "提交事务时出错: " + e.getMessage());
        }
        if (!running) {
            stopCommitTimer();
            closeConnection();
            log(Level.INFO, "SQLite 数据库连接已关闭");
        }
    }

    private void closeConnection() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    try { connection.commit(); } catch (SQLException ignored) {}
                    connection.close();
                }
            } catch (SQLException e) {
                log(Level.WARNING, "关闭数据库连接时出错: " + e.getMessage());
            } finally {
                connection = null;
            }
        }
    }

    public boolean backupDatabase() {
        try {
            Files.createDirectories(backupDir);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupFileName = "redpacket_backup_" + timestamp + ".db";
            Path backupPath = backupDir.resolve(backupFileName);
            if (connection != null && !connection.isClosed()) {
                connection.commit();
            }
            Files.copy(databasePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            cleanupOldBackups();
            log(Level.INFO, "数据库备份成功: " + backupFileName);
            return true;
        } catch (Exception e) {
            log(Level.SEVERE, "数据库备份失败: " + e.getMessage());
            return false;
        }
    }

    public void backupDatabaseAsync(BackupCallback callback) {
        Bukkit.getScheduler().runTaskAsynchronously(getInstance(), () -> {
            boolean success = backupDatabase();
            if (callback != null) {
                Bukkit.getScheduler().runTask(getInstance(), () -> callback.onComplete(success));
            }
        });
    }

    private void cleanupOldBackups() {
        try {
            long sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
            if (Files.exists(backupDir)) {
                Files.list(backupDir)
                        .filter(path -> path.toString().endsWith(".db"))
                        .filter(path -> {
                            try { return Files.getLastModifiedTime(path).toMillis() < sevenDaysAgo; }
                            catch (Exception e) { return false; }
                        })
                        .forEach(path -> {
                            try { Files.delete(path); log(Level.INFO, "删除旧备份: " + path.getFileName()); }
                            catch (Exception e) { log(Level.WARNING, "无法删除旧备份: " + path.getFileName()); }
                        });
            }
        } catch (Exception e) {
            log(Level.WARNING, "清理旧备份时出错: " + e.getMessage());
        }
    }

    public void vacuumDatabase() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.commit();
                connection.createStatement().execute("VACUUM");
                log(Level.INFO, "数据库维护完成 (VACUUM)");
            }
        } catch (Exception e) {
            log(Level.WARNING, "数据库维护失败: " + e.getMessage());
        }
    }

    public void vacuumDatabaseAsync(SimpleCallback callback) {
        Bukkit.getScheduler().runTaskAsynchronously(getInstance(), () -> {
            try {
                vacuumDatabase();
                if (callback != null) {
                    Bukkit.getScheduler().runTask(getInstance(), callback::onSuccess);
                }
            } catch (Exception e) {
                log(Level.WARNING, "异步数据库维护失败: " + e.getMessage());
                if (callback != null) {
                    Bukkit.getScheduler().runTask(getInstance(), () -> callback.onFailure(e));
                }
            }
        });
    }

    public String getDatabaseStats() {
        try {
            if (connection == null || connection.isClosed()) {
                return "数据库连接未打开";
            }
            String countSQL = "SELECT COUNT(*) FROM " + tableName;
            int totalCount = connection.createStatement().executeQuery(countSQL).getInt(1);
            String validSQL = "SELECT COUNT(*) FROM " + tableName + " WHERE expired = 0 AND amount > 0";
            int validCount = connection.createStatement().executeQuery(validSQL).getInt(1);
            long fileSize = Files.exists(databasePath) ? Files.size(databasePath) : 0;
            return String.format("数据库统计: 总红包数=%d, 有效红包数=%d, 文件大小=%.2f MB",
                    totalCount, validCount, fileSize / (1024.0 * 1024.0));
        } catch (Exception e) {
            return "获取数据库统计失败: " + e.getMessage();
        }
    }

    public List<String> getBackupList() {
        List<String> backups = new ArrayList<>();
        try {
            if (Files.exists(backupDir)) {
                Files.list(backupDir)
                        .filter(path -> path.toString().endsWith(".db"))
                        .sorted((p1, p2) -> {
                            try { return Long.compare(
                                    Files.getLastModifiedTime(p2).toMillis(),
                                    Files.getLastModifiedTime(p1).toMillis()); }
                            catch (Exception e) { return 0; }
                        })
                        .forEach(path -> backups.add(path.getFileName().toString()));
            }
        } catch (Exception e) {
            log(Level.WARNING, "获取备份列表失败: " + e.getMessage());
        }
        return backups;
    }

    public interface BackupCallback {
        void onComplete(boolean success);
    }

    public Path getDatabasePath() {
        return databasePath;
    }

    public Path getBackupDir() {
        return backupDir;
    }
}