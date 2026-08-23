package sandtechnology.redpacket.util;

import com.google.gson.reflect.TypeToken;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.bukkit.Bukkit.getServer;
import static sandtechnology.redpacket.RedPacketPlugin.getInstance;
import static sandtechnology.redpacket.util.JsonHelper.getGson;

public class MessageHelper {
    // 使用线程安全的集合存储离线消息
    private static final Map<UUID, List<String>> massageMap = new ConcurrentHashMap<>();
    private static final Map<UUID, List<BaseComponent[]>> componentMassageMap = new ConcurrentHashMap<>();
    private static final Type massageMapType = new TypeToken<Map<UUID, List<String>>>() {}.getType();
    private static final BaseComponent[] baseComponentType = new BaseComponent[]{};

    private MessageHelper() {
    }

    private static void addMassage(UUID uuid, String massage) {
        massageMap.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>()).add(massage);
    }

    private static void addMassage(UUID uuid, BaseComponent... massage) {
        componentMassageMap.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>()).add(massage);
    }

    /**
     * 获取并清除指定玩家的所有离线文本消息
     * @param uuid 玩家 UUID
     * @return 消息队列（可能为 null）
     */
    public static ConcurrentLinkedQueue<String> getAndClearTextMessages(UUID uuid) {
        List<String> list = massageMap.remove(uuid);
        if (list == null) return null;
        return new ConcurrentLinkedQueue<>(list);
    }

    /**
     * 获取并清除指定玩家的所有离线 JSON 消息
     * @param uuid 玩家 UUID
     * @return 消息队列（可能为 null）
     */
    public static ConcurrentLinkedQueue<BaseComponent[]> getAndClearJsonMessages(UUID uuid) {
        List<BaseComponent[]> list = componentMassageMap.remove(uuid);
        if (list == null) return null;
        return new ConcurrentLinkedQueue<>(list);
    }

    // ========== 发送消息的公共方法（在线/离线自适应） ==========

    /**
     * 发送带颜色的文本消息给玩家（离线则缓存）
     */
    public static void sendServiceMsg(OfflinePlayer sender, ChatColor color, String msg) {
        String message = ChatColor.GREEN + "[红包]" + color + msg;
        if (sender.isOnline()) {
            Player player = sender.getPlayer();
            if (player != null) {
                runSync(() -> player.sendMessage(message));
            }
        } else {
            addMassage(sender.getUniqueId(), message);
        }
    }

    /**
     * 发送 JSON 消息给玩家（离线则缓存）
     */
    public static void sendServiceMsg(OfflinePlayer sender, BaseComponent... msg) {
        if (sender.isOnline()) {
            Player player = sender.getPlayer();
            if (player != null) {
                runSync(() -> CompatibilityHelper.sendJSONMessage(player, msg));
            }
        } else {
            addMassage(sender.getUniqueId(), msg);
        }
    }

    /**
     * 发送普通消息（在线）
     */
    public static void sendSimpleMsg(CommandSender sender, ChatColor color, String msg) {
        String message = ChatColor.GREEN + "[红包]" + color + msg;
        if (sender instanceof Player) {
            runSync(() -> sender.sendMessage(message));
        } else {
            sender.sendMessage(message); // 控制台同步发送
        }
    }

    /**
     * 发送 JSON 消息（在线）
     */
    public static void sendSimpleMsg(Player sender, BaseComponent... msg) {
        runSync(() -> CompatibilityHelper.sendJSONMessage(sender, msg));
    }

    /**
     * 广播普通消息给所有在线玩家
     */
    public static void broadcastMsg(ChatColor color, String msg) {
        String message = ChatColor.GREEN + "[红包]" + color + msg;
        runSync(() -> getServer().broadcastMessage(message));
    }

    /**
     * 广播 JSON 消息给所有在线玩家
     */
    public static void broadcastMsg(BaseComponent... msg) {
        List<BaseComponent> components = new ArrayList<>(Arrays.asList(msg));
        components.add(0, new TextComponent(ChatColor.GREEN + "[红包]"));
        BaseComponent[] finalComponents = components.toArray(baseComponentType);
        runSync(() -> {
            for (Player player : getServer().getOnlinePlayers()) {
                CompatibilityHelper.sendJSONMessage(player, finalComponents);
            }
        });
    }

    /**
     * 广播红包公告（标题+副标题）
     */
    public static void broadcastRedPacket(String title, String subtitle) {
        runSync(() -> {
            for (Player player : getServer().getOnlinePlayers()) {
                CompatibilityHelper.playLevelUpSound(player);
                CompatibilityHelper.sendTitle(player, title, subtitle);
            }
        });
    }

    /**
     * 广播专享红包公告（仅特定玩家）
     */
    public static void broadcastSelectiveRedPacket(List<OfflinePlayer> players, String title, String subtitle) {
        runSync(() -> {
            for (OfflinePlayer offlinePlayer : players) {
                if (offlinePlayer.isOnline()) {
                    Player player = offlinePlayer.getPlayer();
                    if (player != null) {
                        CompatibilityHelper.playLevelUpSound(player);
                        CompatibilityHelper.sendTitle(player, title, subtitle);
                    }
                }
            }
        });
    }

    /**
     * 初始化方法，在插件启用/禁用时调用
     * @param status true 加载，false 保存
     */
    public static void setStatus(boolean status) {
        Path path = getInstance().getDataFolder().toPath().resolve("PlayerData.json");
        if (status) {
            if (Files.exists(path)) {
                try {
                    FromJson(Files.readAllLines(path));
                } catch (IOException ex) {
                    throw new RuntimeException("无法加载将要发送给玩家的消息！", ex);
                }
            }
        } else {
            try {
                Files.write(path, getJson(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("无法保存将要发送给玩家的消息！", e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void FromJson(List<String> json) {
        if (json.size() == 2) {
            Map<UUID, List<String>> loadedTextMap = getGson().fromJson(json.get(0), massageMapType);
            Map<UUID, List<String>> loadedComponentMap = getGson().fromJson(json.get(1), massageMapType);

            if (loadedTextMap != null) {
                for (Map.Entry<UUID, List<String>> entry : loadedTextMap.entrySet()) {
                    massageMap.put(entry.getKey(), new CopyOnWriteArrayList<>(entry.getValue()));
                }
            }

            if (loadedComponentMap != null) {
                for (Map.Entry<UUID, List<String>> entry : loadedComponentMap.entrySet()) {
                    List<BaseComponent[]> components = entry.getValue().parallelStream()
                            .map(ComponentSerializer::parse)
                            .collect(Collectors.toCollection(CopyOnWriteArrayList::new));
                    componentMassageMap.put(entry.getKey(), components);
                }
            }
        }
    }

    private static List<String> getJson() {
        Map<UUID, List<String>> componentMap = new ConcurrentHashMap<>();
        componentMassageMap.forEach((uuid, components) -> {
            List<String> serialized = components.parallelStream()
                    .map(ComponentSerializer::toString)
                    .collect(Collectors.toCollection(CopyOnWriteArrayList::new));
            componentMap.put(uuid, serialized);
        });
        return Arrays.asList(
                getGson().toJson(massageMap),
                getGson().toJson(componentMap)
        );
    }

    public static void clearMessages(UUID uuid) {
        massageMap.remove(uuid);
        componentMassageMap.remove(uuid);
    }

    private static void runSync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(getInstance(), task);
        }
    }
}