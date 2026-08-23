package sandtechnology.redpacket.command;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import sandtechnology.redpacket.redpacket.RedPacket;
import sandtechnology.redpacket.session.CreateSession;
import sandtechnology.redpacket.util.IdiomManager;
import sandtechnology.redpacket.util.RedPacketManager;

import java.util.Arrays;
import java.util.List;

import static sandtechnology.redpacket.RedPacketPlugin.getInstance;
import static sandtechnology.redpacket.session.SessionManager.getSessionManager;
import static sandtechnology.redpacket.util.CommonHelper.checkAndDoSomething;
import static sandtechnology.redpacket.util.CommonHelper.emptyFunction;
import static sandtechnology.redpacket.util.EcoAndPermissionHelper.canSet;
import static sandtechnology.redpacket.util.EcoAndPermissionHelper.hasPermission;
import static sandtechnology.redpacket.util.MessageHelper.*;

public class CommandHandler implements TabExecutor {
    private static final CommandHandler commandHandler = new CommandHandler();

    // Folia 环境检测（后面会支持Folia吧...）
    private static final boolean IS_FOLIA;

    static {
        IS_FOLIA = checkFolia();
    }

    public static CommandHandler getCommandHandler() {
        return commandHandler;
    }


    private boolean checkArgs(String[] args, int length, CommandSender sender) {
        return checkAndDoSomething(args.length >= length, emptyFunction, () -> sendSimpleMsg(sender, ChatColor.RED, "命令参数不正确！"));
    }

    private boolean checkSessionAndSetState(Player sender, CreateSession.State state) {
        return checkAndDoSomething(getSessionManager().hasSession(sender) && getSessionManager().getSession(sender).setState(state), emptyFunction, () -> sendSimpleMsg(sender, new ComponentBuilder(ChatColor.GREEN + "创建会话已失效，请点击这里重新创建！").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/redpacket new")).create()));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player && checkArgs(args, 1, sender)) {
            Player player = (Player) sender;
            switch (args[0].toLowerCase()) {
                case "add":
                case "new":
                    if (hasPermission(player, "redpacket.command.new")) {
                        sendSimpleMsg(player, ChatColor.GREEN, "正在创建/拉取红包对话...");
                        sendSimpleMsg(player, getSessionManager().createSession(player).getBuilder().getInfo());
                    }
                    break;
                case "set":
                    if (checkArgs(args, 3, player) && getSessionManager().hasSession(player)) {
                        switch (args[1]) {
                            case "type":
                                switch (args[2].toLowerCase()) {
                                    case "normal":
                                        if (canSet(player, RedPacket.RedPacketType.CommonRedPacket)) {
                                            getSessionManager().getSession(player).getBuilder().type(RedPacket.RedPacketType.CommonRedPacket);
                                        }
                                        break;
                                    case "password":
                                        if (canSet(player, RedPacket.RedPacketType.PasswordRedPacket)) {
                                            getSessionManager().getSession(player).getBuilder().type(RedPacket.RedPacketType.PasswordRedPacket);
                                        }
                                        break;
                                    case "jielong":
                                        if (canSet(player, RedPacket.RedPacketType.JieLongRedPacket)) {
                                            getSessionManager().getSession(player).getBuilder().type(RedPacket.RedPacketType.JieLongRedPacket);
                                            // 成语操作是线程安全的，可以直接调用
                                            getSessionManager().getSession(player).getBuilder().extraData(IdiomManager.getRandomIdiom());
                                        }
                                }
                                break;
                            case "givetype":
                                switch (args[2].toLowerCase()) {
                                    case "fixed":
                                        getSessionManager().getSession(player).getBuilder().giveType(RedPacket.GiveType.FixAmount);
                                        break;
                                    case "luck":
                                        getSessionManager().getSession(player).getBuilder().giveType(RedPacket.GiveType.LuckyAmount);
                                }
                        }
                        sendSimpleMsg(player, getSessionManager().getSession(player).getBuilder().getInfo());
                    }
                    break;
                case "query":
                    if (checkArgs(args, 2, player)) {
                        HoverEvent selectTip = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击以选择").create());
                        switch (args[1].toLowerCase()) {
                            case "type":
                                if (checkSessionAndSetState(player, CreateSession.State.WaitType)) {
                                    sendSimpleMsg(player, ChatColor.GREEN, "请选择红包类型：");
                                    sendSimpleMsg(player,
                                            new ComponentBuilder(ChatColor.GREEN + ChatColor.UNDERLINE.toString() + "普通").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/redpacket set type normal")).event(selectTip)
                                                    .append("  ").reset()
                                                    .append(ChatColor.GREEN + ChatColor.UNDERLINE.toString() + "口令").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/redpacket set type password")).underlined(true).event(selectTip)
                                                    .append("  ").reset()
                                                    .append(ChatColor.GREEN + ChatColor.UNDERLINE.toString() + "接龙").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/redpacket set type jielong")).event(selectTip)
                                                    .create());
                                }
                                break;
                            case "givetype":
                                if (checkSessionAndSetState(player, CreateSession.State.WaitGiveType)) {
                                    sendSimpleMsg(player, ChatColor.GREEN, "请选择给予类型：");
                                    sendSimpleMsg(player,
                                            new ComponentBuilder(ChatColor.GREEN + ChatColor.UNDERLINE.toString() + "固定").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/redpacket set givetype fixed")).event(selectTip)
                                                    .append("  ").reset()
                                                    .append(ChatColor.GREEN + ChatColor.UNDERLINE.toString() + "拼手气").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/redpacket set givetype luck")).event(selectTip)
                                                    .create());

                                }
                                break;
                            case "money":
                                if (checkSessionAndSetState(player, CreateSession.State.WaitMoney)) {
                                    sendSimpleMsg(player, ChatColor.GREEN, "请输入红包总额（小数，比如233.23）：");
                                }
                                break;
                            case "amount":
                                if (checkSessionAndSetState(player, CreateSession.State.WaitAmount)) {
                                    sendSimpleMsg(player, ChatColor.GREEN, "请输入红包数量（整数，比如23）：");
                                }
                                break;
                            case "giver":
                                if (checkSessionAndSetState(player, CreateSession.State.WaitGiver)) {
                                    sendSimpleMsg(player, ChatColor.GREEN, "请输入玩家名称（多个玩家请以英文,分隔）：");
                                }
                                break;
                            case "extradata":
                                if (checkSessionAndSetState(player, CreateSession.State.WaitExtra)) {
                                    sendSimpleMsg(player, ChatColor.GREEN, "请输入" + getSessionManager().getSession(player).getBuilder().getExtraDataInfo() + "：");
                                }
                                break;
                            default:
                                sendSimpleMsg(player, ChatColor.RED, "命令参数不正确！");
                        }
                    }
                    break;
                case "session":
                    if (checkArgs(args, 2, player) && checkSessionAndSetState(player, CreateSession.State.Init) && hasPermission(player, "redpacket.command.session")) {
                        switch (args[1].toLowerCase()) {
                            case "create":
                                // 异步执行红包创建逻辑
                                runAsync(() -> {
                                    if (getSessionManager().getSession(player).getBuilder().isValid()) {
                                        RedPacket redPacket = getSessionManager().getSession(player).create();

                                        // 生成提示信息
                                        String text = ChatColor.GREEN + "玩家" + ChatColor.GOLD + player.getName() + ChatColor.GREEN + "发了一个" + (redPacket.isLimitPlayer() ? "只限" + redPacket.getLimitPlayerList() + "领取的" : "所有人的") + ChatColor.RESET + ChatColor.GREEN + redPacket.getType().getName() + "！ (" + redPacket.getType().getExtraDataName() + "：" + redPacket.getExtraData() + ")  ";
                                        // 不含领取的提示信息
                                        final BaseComponent[] basicMessage = new ComponentBuilder(text).color(net.md_5.bungee.api.ChatColor.GREEN).create();
                                        final ComponentBuilder componentBuilder = new ComponentBuilder(text).color(net.md_5.bungee.api.ChatColor.GREEN);
                                        switch (redPacket.getType()) {
                                            case CommonRedPacket:
                                                componentBuilder.append(ChatColor.GREEN + ChatColor.UNDERLINE.toString() + "点击这里领取").underlined(true).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/redpacket get " + redPacket.getUUID().toString()));
                                                break;
                                            case PasswordRedPacket:
                                                componentBuilder.append(ChatColor.GREEN + ChatColor.UNDERLINE.toString() + "点击这里领取").underlined(true).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, redPacket.getExtraData()));
                                                break;
                                            case JieLongRedPacket:
                                                componentBuilder.append(ChatColor.GREEN + "下一个成语的音节为 " + ChatColor.UNDERLINE.toString() + IdiomManager.getIdiomPinyin(redPacket.getExtraData()));
                                        }

                                        // 对专享红包进行判断，防止游戏体验降低
                                        runSyncGlobal(() -> {
                                            if (redPacket.isLimitPlayer()) {
                                                broadcastSelectiveRedPacket(redPacket.getLimitPlayers(), ChatColor.GREEN + "抢红包啦！", ChatColor.GREEN + "" + "玩家" + ChatColor.GOLD + player.getName() + ChatColor.GREEN + "给你发了一个" + redPacket.getType().getName() + "！");
                                                redPacket.getLimitPlayers().forEach(offlinePlayer -> sendServiceMsg(offlinePlayer, componentBuilder.create()));
                                                Bukkit.getOnlinePlayers().stream().filter(onlinePlayer -> !redPacket.getLimitPlayers().contains(onlinePlayer)).forEach(onlinePlayer -> sendSimpleMsg(onlinePlayer, basicMessage));
                                            } else {
                                                broadcastRedPacket(ChatColor.GREEN + "抢红包啦！", ChatColor.GREEN + "" + "玩家" + ChatColor.GOLD + player.getName() + ChatColor.GREEN + "发了一个" + redPacket.getType().getName() + "！");
                                                broadcastMsg(componentBuilder.create());
                                            }
                                        });
                                    }
                                });
                                break;
                            case "cancel":
                                getSessionManager().getSession(player).cancel();
                                sendSimpleMsg(player, ChatColor.YELLOW, "该会话已取消");
                        }
                    }
                    break;
                case "get":
                    if (checkArgs(args, 2, player) && hasPermission(player, "redpacket.command.get")) {
                        runAsync(() -> {
                            RedPacketManager.getRedPacketManager().getRedPackets().stream()
                                    .filter(packet -> packet.getUUID().toString().equals(args[1]))
                                    .forEach(redPacket -> {
                                        runSync(player, () -> redPacket.giveIfValid(player, ""));
                                    });
                        });
                    }
                    break;
                case "info":
                    // TODO: 实现红包信息查询
                    sendSimpleMsg(player, ChatColor.YELLOW, "此功能暂未实现");
                    break;
                case "help":
                    sendSimpleMsg(player, ChatColor.GREEN,
                            "帮助：\n" +
                                    "/redpacket [add/new] ——创建红包\n" +
                                    "其他的命令为内部使用");
                    break;
                case "reload":
                    if (hasPermission(player, "redpacket.command.reload")) {
                        checkAndDoSomething(getInstance().reload(),
                                () -> sendSimpleMsg(player, ChatColor.GREEN, "插件重载成功！"),
                                () -> sendSimpleMsg(player, ChatColor.RED, "出现错误，请查看控制台。"));
                    }
                    break;

                default:
                    sendSimpleMsg(player, ChatColor.RED, "未知命令，使用 /redpacket help 查看帮助");
            }
        } else if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行！");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("new", "add", "reload", "help", "get", "info");
        } else {
            return null;
        }
    }

    private void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(getInstance(), task);
    }

    private void runSync(Player player, Runnable task) {
        Bukkit.getScheduler().runTask(getInstance(), task);
    }

    private void runSyncGlobal(Runnable task) {
        Bukkit.getScheduler().runTask(getInstance(), task);
    }

    private static boolean checkFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void runSafe(Player player, Runnable task) {
        Bukkit.getScheduler().runTask(getInstance(), task);
    }
}