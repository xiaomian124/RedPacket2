package sandtechnology.redpacket.util;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class CompatibilityHelper {

    private CompatibilityHelper() {
    }

    public static void playLevelUpSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 100, 1);
    }

    public static void playMeowSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 100, 1);
    }

    public static void sendTitle(Player player, String title, String subtitle) {
        player.sendTitle(title, subtitle, -1, -1, -1);
    }

    public static void sendJSONMessage(Player player, BaseComponent... components) {
        player.spigot().sendMessage(components);
    }

    public static void sendJSONMessageBatch(Player player, BaseComponent[]... componentsArray) {
        for (BaseComponent[] components : componentsArray) {
            player.spigot().sendMessage(components);
        }
    }

    public static void sendActionBar(Player player, String message) {
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        } catch (Exception e) {
            player.sendMessage(message);
        }
    }

    public static void playParticle(Player player, String particleName, int count) {
    }

    public static int getMinecraftVersion() {
        return 26;
    }

    // Folia...
    public static boolean isFolia() {
        return false;
    }

    @Deprecated
    public static void runSafe(Player player, Runnable task) {
        task.run();
    }

    public static void setup() {
    }
}