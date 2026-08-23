package sandtechnology.redpacket.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.SQLException;

import static sandtechnology.redpacket.RedPacketPlugin.config;
import static sandtechnology.redpacket.RedPacketPlugin.getInstance;
import static sandtechnology.redpacket.RedPacketPlugin.log;

public class MysqlManager extends AbstractDatabaseManager {

    private HikariDataSource dataSource;

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final String arguments;

    public MysqlManager(String tableName) {
        this.host = config().getString("Database.IP", "localhost");
        this.port = config().getInt("Database.Port", 3306);
        this.database = config().getString("Database.DatabaseName", "redpacket");
        this.username = config().getString("Database.UserName", "root");
        this.password = config().getString("Database.Password", "");
        this.arguments = config().getString("Database.MySQLArgument", "null");

        setup(tableName);
    }

    @Override
    public Connection getConnection() {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            log(java.util.logging.Level.SEVERE, "获取数据库连接失败: " + e.getMessage());
            throw new RuntimeException("数据库连接错误", e);
        }
    }

    @Override
    void setup(String tableName) {
        try {
            this.tableName = tableName;
            initializeConnectionPool();
            createTable();
            createIndex();
            startCommitTimer();
            setRunning(true);
            log(java.util.logging.Level.INFO, "MySQL 数据库连接池初始化完成");
        } catch (Exception ex) {
            log(java.util.logging.Level.SEVERE, "数据库初始化出现错误: " + ex.getMessage());
            throw new RuntimeException("数据库初始化出现错误，将关闭本插件！", ex);
        }
    }

    private void initializeConnectionPool() {
        HikariConfig config = new HikariConfig();
        StringBuilder jdbcUrl = new StringBuilder();
        jdbcUrl.append("jdbc:mysql://")
                .append(host)
                .append(":")
                .append(port)
                .append("/")
                .append(database);

        if (!arguments.equals("null") && !arguments.trim().isEmpty()) {
            jdbcUrl.append(arguments.startsWith("?") ? "" : "?").append(arguments);
        }

        config.setJdbcUrl(jdbcUrl.toString());
        config.setUsername(username);
        config.setPassword(password);

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(3);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setValidationTimeout(5000);

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("RedPacket-MySQL-Pool");

        dataSource = new HikariDataSource(config);
    }

    private void createTable() {
        String createTableSQL =
                "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                        "UUID CHAR(36) PRIMARY KEY," +
                        "playerUUID CHAR(36) NOT NULL," +
                        "giveType TEXT NOT NULL," +
                        "RedPacketType TEXT NOT NULL," +
                        "amount INTEGER NOT NULL," +
                        "money DOUBLE NOT NULL," +
                        "moneyMap LONGTEXT NOT NULL," +
                        "extraData MEDIUMTEXT NOT NULL," +
                        "givers TEXT NOT NULL," +
                        "expireTime BIGINT NOT NULL," +
                        "timeZone TEXT NOT NULL," +
                        "expired INTEGER NOT NULL)" +
                        "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
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
        } catch (Exception e) {
            log(java.util.logging.Level.WARNING, "创建索引失败，可能已存在: " + e.getMessage());
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
                e.printStackTrace();
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
            commit();
        } catch (Exception e) {
            log(java.util.logging.Level.WARNING, "提交事务时出错: " + e.getMessage());
        }
        if (!running) {
            stopCommitTimer();
            closeConnectionPool();
            log(java.util.logging.Level.INFO, "MySQL 连接池已关闭");
        }
    }

    private void closeConnectionPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            dataSource = null;
        }
    }

    public String getPoolStats() {
        if (dataSource == null) {
            return "连接池未初始化";
        }
        return String.format(
                "连接池状态: 活动连接=%d, 空闲连接=%d, 等待连接=%d, 总连接=%d",
                dataSource.getHikariPoolMXBean().getActiveConnections(),
                dataSource.getHikariPoolMXBean().getIdleConnections(),
                dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection(),
                dataSource.getHikariPoolMXBean().getTotalConnections()
        );
    }

    public boolean healthCheck() {
        if (dataSource == null || dataSource.isClosed()) {
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            return false;
        }
    }

    public void healthCheckAsync(HealthCheckCallback callback) {
        Bukkit.getScheduler().runTaskAsynchronously(getInstance(), () -> {
            boolean healthy = healthCheck();
            if (callback != null) {
                Bukkit.getScheduler().runTask(getInstance(), () -> callback.onResult(healthy));
            }
        });
    }

    public interface HealthCheckCallback {
        void onResult(boolean isHealthy);
    }

    public String getConnectionInfo() {
        return String.format("MySQL连接: %s@%s:%d/%s", username, host, port, database);
    }
}