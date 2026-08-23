package sandtechnology.redpacket.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import sandtechnology.redpacket.redpacket.RedPacket;
import sandtechnology.redpacket.session.CreateSession;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static sandtechnology.redpacket.RedPacketPlugin.getInstance;
import static sandtechnology.redpacket.session.SessionManager.getSessionManager;
import static sandtechnology.redpacket.util.RedPacketManager.getRedPacketManager;

public class ChatListener implements Listener {

    private static final CreateSession.State[] inputNeededState = {
            CreateSession.State.WaitAmount,
            CreateSession.State.WaitExtra,
            CreateSession.State.WaitGiver,
            CreateSession.State.WaitMoney
    };

    public ChatListener() {
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // 判断是否在输入创建红包的数据
        if (getSessionManager().hasSession(player)) {
            CreateSession session = getSessionManager().getSession(player);
            CreateSession.State state = session.getState();

            if (Arrays.stream(inputNeededState).anyMatch(s -> s == state)) {
                // 会话处理必须在主线程执行
                runSync(player, () -> session.parse(player, message));
                event.setCancelled(true);
                return; // 会话输入不再参与红包检查
            }
        }

        // 检查红包（接龙/口令红包）
        checkRedPacket(event);
    }

    private void checkRedPacket(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // 筛选需要检查的红包类型（接龙和口令）
        List<RedPacket> redPacketsToCheck = getRedPacketManager().getRedPackets().stream()
                .filter(redPacket -> redPacket.getType() == RedPacket.RedPacketType.JieLongRedPacket ||
                        redPacket.getType() == RedPacket.RedPacketType.PasswordRedPacket)
                .collect(Collectors.toList());

        if (redPacketsToCheck.isEmpty()) {
            return;
        }

        // 异步执行红包检查（不会阻塞主线程）
        runAsync(() -> {
            for (RedPacket redPacket : redPacketsToCheck) {
                // giveIfValid 必须同步执行（修改玩家数据）
                runSync(player, () -> redPacket.giveIfValid(player, message));
            }
        });
    }

    private void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(getInstance(), task);
    }

    private void runSync(Player player, Runnable task) {
        Bukkit.getScheduler().runTask(getInstance(), task);
    }
}