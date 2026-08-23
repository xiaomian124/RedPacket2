package sandtechnology.redpacket.database;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import sandtechnology.redpacket.redpacket.RedPacket;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static sandtechnology.redpacket.RedPacketPlugin.getInstance;

public abstract class AbstractDatabaseManager {

    // 连接池或连接管理器（具体由子类实现）
    Connection connection;
    String tableName;
    private volatile boolean commiting;

    // 改为 protected，允许子类访问
    protected volatile boolean running;

    // 定时任务引用（BukkitTask）
    private org.bukkit.scheduler.BukkitTask scheduledTask;

    private void sleep() {
        try {
            Thread.sleep(100L);
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * 执行数据库更新操作
     */
    void executeUpdate(String sql) {
        try {
            while (commiting) {
                sleep();
            }
            getConnection().createStatement().executeUpdate(sql);
        } catch (SQLException ex) {
            throw new RuntimeException("SQL语句执行错误！语句：" + sql, ex);
        }
    }

    /**
     * 异步执行数据库更新操作
     */
    void executeUpdateAsync(String sql, Runnable callback) {
        Bukkit.getScheduler().runTaskAsynchronously(getInstance(), () -> {
            try {
                executeUpdate(sql);
                if (callback != null) {
                    Bukkit.getScheduler().runTask(getInstance(), callback);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    abstract void setup(String tableName);

    private ResultSet executeQuery(String sql) {
        try {
            return getConnection().createStatement().executeQuery(sql);
        } catch (SQLException ex) {
            throw new RuntimeException("SQL语句执行错误！语句：" + sql, ex);
        }
    }

    /**
     * 异步执行数据库查询操作
     */
    void executeQueryAsync(String sql, QueryCallback callback) {
        Bukkit.getScheduler().runTaskAsynchronously(getInstance(), () -> {
            try {
                ResultSet resultSet = executeQuery(sql);
                if (callback != null) {
                    Bukkit.getScheduler().runTask(getInstance(), () -> {
                        try {
                            callback.onComplete(resultSet);
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (callback != null) {
                    Bukkit.getScheduler().runTask(getInstance(), () -> {
                        try {
                            callback.onError(e);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                }
            }
        });
    }

    void startCommitTimer() {
        scheduledTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                getInstance(),
                () -> {
                    long time = System.currentTimeMillis();
                    if (running) {
                        commit();
                    }
                },
                0L,   // 初始延迟（tick）
                200L  // 执行间隔（200 tick = 10秒）
        );
    }

    /**
     * 设置运行状态（子类可重写，但需调用父类逻辑）
     */
    synchronized public void setRunning(boolean running) {
        this.running = running;
        commit();
        if (!running) {
            stopCommitTimer();
            close();
        }
    }

    /**
     * 停止提交定时任务（改为 protected，供子类调用）
     */
    protected void stopCommitTimer() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    /**
     * 关闭数据库连接（改为 protected，供子类重写时调用）
     */
    protected synchronized void close() {
        try {
            getConnection().close();
        } catch (SQLException ex) {
            throw new RuntimeException("数据库连接关闭失败！", ex);
        }
    }

    public Connection getConnection() {
        return connection;
    }


    protected synchronized void commit() {
        try {
            commiting = true;
            getConnection().commit();
            commiting = false;
        } catch (SQLException ex) {
            throw new RuntimeException("数据库提交更改失败！", ex);
        }
    }

    public void store(RedPacket redPacket) {
        executeUpdate(redPacket.toInsertSQL(tableName));
    }

    public void storeAsync(RedPacket redPacket, Runnable callback) {
        executeUpdateAsync(redPacket.toInsertSQL(tableName), callback);
    }

    public void delete(RedPacket redPacket) {
        executeUpdate("DELETE FROM " + tableName + " Where UUID='" + redPacket.getUUID().toString() + "'");
    }

    public void deleteAsync(RedPacket redPacket, Runnable callback) {
        executeUpdateAsync("DELETE FROM " + tableName + " Where UUID='" + redPacket.getUUID().toString() + "'", callback);
    }

    public void update(RedPacket redPacket) {
        executeUpdate(redPacket.toUpdateSQL(tableName));
    }

    public void updateAsync(RedPacket redPacket, Runnable callback) {
        executeUpdateAsync(redPacket.toUpdateSQL(tableName), callback);
    }

    public List<RedPacket> getValid() {
        ResultSet resultSet = executeQuery("Select * from " + tableName + " where expired=0 and amount!=0");
        return RedPacket.fromSQL(resultSet);
    }

    public void getValidAsync(QueryCallback callback) {
        executeQueryAsync("Select * from " + tableName + " where expired=0 and amount!=0", new QueryCallback() {
            @Override
            public void onComplete(ResultSet resultSet) throws SQLException {
                List<RedPacket> redPackets = RedPacket.fromSQL(resultSet);
                callback.onComplete(resultSet);
            }

            @Override
            public void onError(Exception e) {
                callback.onError(e);
            }
        });
    }

    public RedPacket get(Player player) {
        List<RedPacket> packets = getAll(player, 1);
        return packets.isEmpty() ? null : packets.get(0);
    }

    public List<RedPacket> getAll(Player player, int amount) {
        return getNext(player, amount, 0);
    }

    private List<RedPacket> getNext(Player player, int amount, int offset) {
        ResultSet resultSet = executeQuery("Select * from " + tableName + " where playerUUID='" + player.getUniqueId().toString() + "' order by expireTime desc LIMIT " + amount + " OFFSET " + offset);
        return RedPacket.fromSQL(resultSet);
    }

    public void getPlayerRedPacketsAsync(Player player, int amount, int offset, QueryCallback callback) {
        String sql = "Select * from " + tableName + " where playerUUID='" + player.getUniqueId().toString() + "' order by expireTime desc LIMIT " + amount + " OFFSET " + offset;
        executeQueryAsync(sql, callback);
    }

    public interface QueryCallback {
        void onComplete(ResultSet resultSet) throws SQLException;
        void onError(Exception e);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onFailure(Exception e);
    }
}