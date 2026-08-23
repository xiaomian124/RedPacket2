package sandtechnology.redpacket.listener;

import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import sandtechnology.redpacket.util.MessageHelper;

import java.util.concurrent.ConcurrentLinkedQueue;

import static sandtechnology.redpacket.util.MessageHelper.sendServiceMsg;

public class MessageSender implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        ConcurrentLinkedQueue<String> textMessages = MessageHelper.getAndClearTextMessages(event.getPlayer().getUniqueId());
        ConcurrentLinkedQueue<BaseComponent[]> jsonMessages = MessageHelper.getAndClearJsonMessages(event.getPlayer().getUniqueId());

        if (textMessages != null && !textMessages.isEmpty()) {
            for (String message : textMessages) {
                sendServiceMsg(event.getPlayer(), ChatColor.GREEN, message);
            }
        }

        if (jsonMessages != null && !jsonMessages.isEmpty()) {
            for (BaseComponent[] components : jsonMessages) {
                sendServiceMsg(event.getPlayer(), components);
            }
        }
    }
}