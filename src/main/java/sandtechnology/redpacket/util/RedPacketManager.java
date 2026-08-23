package sandtechnology.redpacket.util;

import org.bukkit.Bukkit;
import sandtechnology.redpacket.redpacket.RedPacket;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static sandtechnology.redpacket.RedPacketPlugin.getDatabaseManager;
import static sandtechnology.redpacket.RedPacketPlugin.getInstance;

/**
 * 红包管理（纯 Bukkit 实现）
 */
public class RedPacketManager {

    private static final RedPacketManager redPacketManager = new RedPacketManager();
    private final List<RedPacket> redPackets = new CopyOnWriteArrayList<>();

    // 定时任务引用（BukkitTask）
    private org.bukkit.scheduler.BukkitTask scheduledTask;

    private RedPacketManager() {
    }

    public static RedPacketManager getRedPacketManager() {
        return redPacketManager;
    }

    public void setup() {
        // 加载有效的红包
        redPackets.addAll(getDatabaseManager().getValid());

        // 初始化时检查过期红包（必须在主线程执行）
        runSync(() -> checkExpiredRedPackets());

        // 设置定时任务检查过期红包（异步定时）
        setupExpirationCheckTask();
    }

    /**
     * 检查过期红包并退款（必须在主线程执行，因为涉及经济操作）
     */
    private void checkExpiredRedPackets() {
        for (RedPacket redPacket : redPackets) {
            redPacket.refundIfExpired();
        }
    }

    /**
     * 设置过期检查定时任务（异步定时）
     */
    private void setupExpirationCheckTask() {
        // 使用 Bukkit 异步定时器，每 200 tick（10秒）检查一次
        scheduledTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                getInstance(),
                () -> {
                    // 由于 refundIfExpired 涉及经济操作，必须调度到主线程执行
                    runSync(this::runExpirationCheck);
                },
                200L,   // 初始延迟（200 tick = 10秒）
                200L    // 执行间隔（200 tick = 10秒）
        );
    }

    /**
     * 执行过期检查（在主线程执行）
     */
    private void runExpirationCheck() {
        for (RedPacket redPacket : redPackets) {
            redPacket.refundIfExpired();
        }
    }

    /**
     * 添加红包
     */
    public void add(RedPacket redPacket) {
        getDatabaseManager().store(redPacket);
        redPackets.add(redPacket);
    }

    /**
     * 移除红包
     */
    public void remove(RedPacket redPacket) {
        redPackets.remove(redPacket);
    }

    /**
     * 安全地移除红包
     */
    public boolean safeRemove(RedPacket redPacket) {
        return redPackets.remove(redPacket);
    }

    public List<RedPacket> getRedPackets() {
        return redPackets;
    }

    /**
     * 清理所有红包（插件禁用时调用）
     */
    public void cleanup() {
        // 停止定时任务
        stopExpirationCheckTask();

        // 清理所有红包（退款操作必须在主线程执行）
        runSync(() -> {
            for (RedPacket redPacket : redPackets) {
                redPacket.refundIfExpired();
            }
        });

        // 清空列表
        redPackets.clear();
    }

    /**
     * 停止过期检查任务
     */
    private void stopExpirationCheckTask() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    /**
     * 确保任务在主线程执行
     */
    private void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(getInstance(), task);
        }
    }
}