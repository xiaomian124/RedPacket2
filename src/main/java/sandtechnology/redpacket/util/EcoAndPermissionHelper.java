package sandtechnology.redpacket.util;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import sandtechnology.redpacket.redpacket.RedPacket;
import sandtechnology.redpacket.RedPacketPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

import static org.bukkit.Bukkit.getServer;
import static sandtechnology.redpacket.RedPacketPlugin.log;
import static sandtechnology.redpacket.util.CommonHelper.checkAndDoSomething;
import static sandtechnology.redpacket.util.CommonHelper.emptyFunction;
import static sandtechnology.redpacket.util.MessageHelper.sendSimpleMsg;

/**
 * 权限+经济的工具类
 */
public class EcoAndPermissionHelper {
    static private Economy eco;
    static private Permission per;

    private EcoAndPermissionHelper() {
    }

    /**
     * 获取目标服务
     *
     * @param service 要获取的服务
     * @param <T>     服务类型
     * @return 服务提供者
     */
    private static <T> T getRegisteredProvider(Class<T> service) {
        RegisteredServiceProvider<T> serviceProvider = getServer().getServicesManager().getRegistration(service);
        if (serviceProvider != null && serviceProvider.getProvider() != null) {
            return serviceProvider.getProvider();
        }
        return null;
    }

    public static void setup() {
        log(Level.INFO, "初始化经济与权限支持....");
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            per = getRegisteredProvider(Permission.class);
            if (per != null) {
                log(Level.INFO, "找到权限插件：" + per.getName());
            } else {
                log(Level.SEVERE, "未找到支持Vault的权限插件！将使用原版API！");
            }
            eco = getRegisteredProvider(Economy.class);
            if (eco != null) {
                log(Level.INFO, "找到经济插件：" + eco.getName());
            } else {
                log(Level.SEVERE, "未找到支持Vault的经济插件！");
            }
        } else {
            log(Level.SEVERE, "未找到Vault！此插件将被禁用！");
        }

        if (getServer().getPluginManager().getPlugin("Vault") == null || eco == null) {
            throw new RuntimeException("当前插件运行环境不符合！将禁用本插件！");
        }
    }

    public static Economy getEco() {
        return eco;
    }

    public static boolean hasPermission(Player player, String perNode) {
        // 如果已经在主线程，直接执行
        if (org.bukkit.Bukkit.isPrimaryThread()) {
            return hasPermissionSync(player, perNode);
        }

        // 否则调度到主线程执行并等待结果
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        org.bukkit.Bukkit.getScheduler().runTask(RedPacketPlugin.getInstance(), () -> {
            try {
                future.complete(hasPermissionSync(player, perNode));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean hasPermissionSync(Player player, String perNode) {
        boolean hasPerNode = per == null ? player.hasPermission(perNode) : per.playerHas(player, perNode);
        return checkAndDoSomething(hasPerNode, emptyFunction, () ->
                sendSimpleMsg(player, ChatColor.RED, "你没有进行此操作的权限！"));
    }

    public static boolean withdrawPlayer(Player player, double amount) {
        if (eco == null) return false;
        return eco.withdrawPlayer(player, amount).transactionSuccess();
    }

    public static boolean depositPlayer(Player player, double amount) {
        if (eco == null) return false;
        return eco.depositPlayer(player, amount).transactionSuccess();
    }

    public static double getBalance(Player player) {
        if (eco == null) return 0;
        return eco.getBalance(player);
    }

    public static boolean canSet(Player sender, RedPacket.RedPacketType redPacket) {
        return hasPermission(sender, "redpacket.set." + redPacket.name().toLowerCase());
    }

    public static boolean canGet(Player sender, RedPacket.RedPacketType redPacket) {
        return hasPermission(sender, "redpacket.get." + redPacket.name().toLowerCase());
    }

    public static void executeEconomyAsync(Player player, EconomyTask task) {
        org.bukkit.Bukkit.getScheduler().runTask(RedPacketPlugin.getInstance(), () -> {
            task.execute(player);
        });
    }

    @FunctionalInterface
    public interface EconomyTask {
        void execute(Player player);
    }
}