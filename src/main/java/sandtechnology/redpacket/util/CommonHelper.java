package sandtechnology.redpacket.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static sandtechnology.redpacket.RedPacketPlugin.getInstance;

/**
 * 一般工具类
 */
public class CommonHelper {
    // 空函数占位符
    public static final voidFunction emptyFunction = () -> {};

    private CommonHelper() {
    }

    /**
     * 懒人辅助方法
     *
     * @param check     输入的布尔值
     * @param doIfTrue  当check为true时执行的函数
     * @param doIfFalse 当check为false时执行的函数
     * @return check的布尔值
     */
    public static boolean checkAndDoSomething(boolean check, voidFunction doIfTrue, voidFunction doIfFalse) {
        if (check) {
            doIfTrue.func();
        } else {
            doIfFalse.func();
        }
        return check;
    }

    /**
     * 执行命令（同步，返回执行结果）
     * 如果当前不在主线程，则调度到主线程并等待结果
     *
     * @param start 指令名称
     * @param args  参数
     * @return 命令是否成功执行
     */
    public static boolean executeCommand(String start, String... args) {
        String command = start + " " + String.join(" ", args);

        if (Bukkit.isPrimaryThread()) {
            return Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(getInstance(), () -> {
            try {
                boolean result = Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
                future.complete(result);
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

    /**
     * 异步执行命令（不等待结果，且不会阻塞调用线程）
     * 命令本身会在主线程执行，但调度是异步的
     *
     * @param start 指令名称
     * @param args  参数
     */
    public static void executeCommandAsync(String start, String... args) {
        String command = start + " " + String.join(" ", args);
        Bukkit.getScheduler().runTask(getInstance(), () -> {
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
        });
    }

    /**
     * 根据环境选择执行方式（同步/异步）
     * 同步任务在主线程执行，异步任务在异步线程执行
     *
     * @param syncTask  同步任务（在主线程执行）
     * @param asyncTask 异步任务（在异步线程执行，可为 null）
     */
    public static void executeBasedOnContext(Runnable syncTask, Runnable asyncTask) {
        Bukkit.getScheduler().runTask(getInstance(), syncTask);

        if (asyncTask != null) {
            Bukkit.getScheduler().runTaskAsynchronously(getInstance(), asyncTask);
        }
    }

    /**
     * void函数
     */
    @FunctionalInterface
    public interface voidFunction {
        void func();
    }
}