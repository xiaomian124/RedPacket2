package sandtechnology.redpacket.redpacket;

import com.google.gson.reflect.TypeToken;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import sandtechnology.redpacket.util.CompatibilityHelper;
import sandtechnology.redpacket.util.IdiomManager;
import sandtechnology.redpacket.util.OperatorHelper;

import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static sandtechnology.redpacket.RedPacketPlugin.getDatabaseManager;
import static sandtechnology.redpacket.RedPacketPlugin.getInstance;
import static sandtechnology.redpacket.util.EcoAndPermissionHelper.canGet;
import static sandtechnology.redpacket.util.EcoAndPermissionHelper.getEco;
import static sandtechnology.redpacket.util.IdiomManager.getIdiomPinyin;
import static sandtechnology.redpacket.util.IdiomManager.isValidSequence;
import static sandtechnology.redpacket.util.JsonHelper.getGson;
import static sandtechnology.redpacket.util.MessageHelper.broadcastMsg;
import static sandtechnology.redpacket.util.MessageHelper.sendServiceMsg;
import static sandtechnology.redpacket.util.MessageHelper.sendSimpleMsg;
import static sandtechnology.redpacket.util.OperatorHelper.add;
import static sandtechnology.redpacket.util.OperatorHelper.divide;
import static sandtechnology.redpacket.util.OperatorHelper.multiply;
import static sandtechnology.redpacket.util.OperatorHelper.toTwoPrecision;
import static sandtechnology.redpacket.util.RedPacketManager.getRedPacketManager;

/**
 *
 * @author sandtechnology,xiaomian124
 * 红包主类，包含N个类型红包的实现
 *
 */
public class RedPacket implements Comparator<RedPacket>, Comparable<RedPacket> {


    private static final Random random = new Random();
    private static final Type moneyMapType = new TypeToken<LinkedHashMap<UUID, Double>>() {}.getType();
    private static final Type giversType = new TypeToken<HashSet<UUID>>() {}.getType();
    private final OfflinePlayer player;
    private final RedPacketType type;
    private final GiveType giveType;
    private final UUID uuid;
    private final Set<UUID> givers;
    private final Map<UUID, Double> moneyMap;
    private int money;
    private int amount;
    private String extraData;
    private final long expireTime;
    private boolean expired;
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("YYYY/MM/dd HH:dd:ss.SSS O");
    /**
     * 创建红包对象，只能通过内置的Builder创建
     *
     * @param uuid       红包唯一识别码
     * @param player     发起红包的玩家
     * @param giveType   给予的类型
     * @param type       红包类型
     * @param amount     红包数量
     * @param money      红包总额
     * @param moneyMap   记录玩家领取红包的数据映射
     * @param givers     能领取红包的玩家
     * @param extraData  额外数据，存储口令、祝福、成语...
     * @param timeZone   时区，实际上是ZoneId
     * @param expireTime 过期时间，以1970-01-01 00:00 UTC到当前时区的时间的毫秒数存储
     */
    private RedPacket(UUID uuid, OfflinePlayer player, GiveType giveType, RedPacketType type, int amount, double money, Map<UUID, Double> moneyMap, String extraData,Set<UUID> givers, long expireTime,ZoneId timeZone, boolean expired) {
        this.player = player;
        //注：为防止精度问题，此处将其乘以100来使用int存储
        this.money = (int) multiply(money, 100);
        this.giveType = giveType;
        this.type = type;
        this.amount = amount;
        this.uuid = uuid;
        this.moneyMap = moneyMap;
        this.extraData = extraData;
        this.givers=givers;
        //防止反序列化时的时区变化
        this.expireTime = Instant.ofEpochMilli(expireTime).atZone(timeZone).withZoneSameLocal(ZoneId.systemDefault()).toInstant().toEpochMilli();
        this.expired = expired;
    }

    /**
     * 将结果集序列化为红包
     *
     * @param sqlData 结果集
     * @return 包含红包的列表，如无红包将为空列表
     */
    public static List<RedPacket> fromSQL(ResultSet sqlData) {
        List<RedPacket> list = new ArrayList<>();
        try {
            while (sqlData.next()) {
                int i = 0;
                //UUID,playerUUID,RedPacketType,giveType,amount,money,moneyMap,extraData,className,expireTime,expired
                list.add(new Builder()
                        //1
                        .uuid(UUID.fromString(sqlData.getString(++i)))
                        //2
                        .player(Bukkit.getOfflinePlayer(UUID.fromString(sqlData.getString(++i))))
                        //3
                        .giveType(GiveType.valueOf(sqlData.getString(++i)))
                        //4
                        .type(RedPacketType.valueOf(sqlData.getString(++i)))
                        //5....
                        .amount(sqlData.getInt(++i))
                        .money(sqlData.getDouble(++i))
                        .moneyMap(getGson().fromJson(sqlData.getString(++i), moneyMapType))
                        .extraData(sqlData.getString(++i))
                        .givers(getGson().<HashSet<UUID>>fromJson(sqlData.getString(++i),giversType))
                        .expireTime(sqlData.getLong(++i))
                        .timeZone(ZoneId.of(sqlData.getString(++i)))
                        .expired(boolFromInt(sqlData.getInt(++i)))
                        .build());
            }
        } catch (Exception e) {
            throw new RuntimeException("SQL解析错误！", e);
        }
        return list;
    }

    public boolean isLimitPlayer() {
        return !givers.isEmpty();
    }

    public String getLimitPlayerList() {
        return givers.parallelStream().map(Bukkit::getOfflinePlayer).map(OfflinePlayer::getName).collect(Collectors.joining(","));
    }

    public List<OfflinePlayer> getLimitPlayers() {
        return givers.parallelStream().map(Bukkit::getOfflinePlayer).collect(Collectors.toList());
    }
    private static int boolToInt(boolean bool) {
        return bool ? 1 : 0;
    }

    private static boolean boolFromInt(int i) {
        return i == 1;
    }

    @Override
    public int compare(RedPacket o1, RedPacket o2) {
        return o1.compareTo(o2);
    }

    @Override
    public int compareTo(RedPacket o) {
        return Long.compare(this.expireTime, o.expireTime);
    }

    public OfflinePlayer getPlayer() {
        return player;
    }

    public String getExtraData() {
        return extraData;
    }

    public RedPacketType getType() {
        return type;
    }

    /**
     * 判断红包领取条件
     *
     * @param player 要领取红包的玩家
     * @param extra  用于判断的额外数据
     */
    synchronized public void giveIfValid(Player player, String extra) {
        //排除不在可领取红包列表中的玩家
        if (!givers.isEmpty() && !givers.contains(player.getUniqueId()) && canGet(player, type)) {
            if (type == RedPacketType.CommonRedPacket) {
                sendSimpleMsg(player, ChatColor.RED, "你无法领取此红包！");
            }
            return;
        }

        if (!moneyMap.containsKey(player.getUniqueId())) {
            switch (type) {
                case JieLongRedPacket:
                    if (isValidSequence(extraData, extra)) {
                        this.extraData = extra;
                        giveMoney(player);
                    }
                    break;
                case PasswordRedPacket:
                    if (extra.equals(extraData)) {
                        giveMoney(player);
                    }
                    break;
                default:
                    giveMoney(player);
            }
        } else {
            if (type == RedPacketType.CommonRedPacket) {
                sendSimpleMsg(player, ChatColor.RED, "你已领取此红包！");
            }
        }
    }


    /**
     * 红包过期自动退款
     */
    synchronized public void refundIfExpired() {
        try {
            if (System.currentTimeMillis() > expireTime && !expired && amount != 0 && getInstance().getConfig().getBoolean("RedPacket.Expired")) {
                sendServiceMsg(player, ChatColor.GREEN, "您的红包已过期，已退还" + getCurrentMoney() + "元");
                getEco().depositPlayer(player, getCurrentMoney());
                expired = true;
                getDatabaseManager().update(this);
                getRedPacketManager().remove(this);
            }
        } catch (Exception e) {
            throw new RuntimeException("红包退还失败！", e);
        }
    }

    public int getCurrentAmount() {
        return amount;
    }

    public double getCurrentMoney() {
        return divide(money, 100);
    }

    private int getAmount() {
        return amount + moneyMap.size();
    }

    public UUID getUUID() {
        return uuid;
    }

    public double getMoney() {
        return add(getCurrentMoney(), moneyMap.values().stream().reduce(OperatorHelper::add).orElse(0.0));
    }

    /**
     * 给予红包给对应玩家
     * 使用算法：微信红包的随机算法是怎样实现的？ - 陈鹏的回答 - 知乎
     * <a href="https://www.zhihu.com/question/22625187/answer/85530416">点击这里</a>
     *
     * @param player 要给予红包的玩家
     */
    synchronized public void giveMoney(Player player) {
        if (System.currentTimeMillis() >= expireTime) {
            return;
        }

        int value;
        if (amount > 1) {
            if (giveType.equals(GiveType.LuckyAmount)) {
                //Math.max((int)(toTwoPrecision(random.nextDouble())/((money/amount)*2),1)
                //你问为什么不乘0.01？random.nextDouble()已经帮我乘了233
                value = Math.max((int) (multiply(toTwoPrecision(random.nextDouble()), multiply(divide(money, amount), 2))), 1);
            } else {
                value = (int) divide(money, amount);
            }
        } else {
            value = money;
        }
        money -= value;
        double giveMoney = multiply(value, 0.01);
        getEco().depositPlayer(player, giveMoney);
        moneyMap.put(player.getUniqueId(), giveMoney);
        Bukkit.getScheduler().runTask(getInstance(), () -> CompatibilityHelper.playMeowSound(player));
        broadcastMsg(ChatColor.YELLOW,
                "玩家" + ChatColor.GOLD + player.getName() +
                        ChatColor.YELLOW + "抢了" + ChatColor.GOLD + this.player.getName() + ChatColor.YELLOW + "的红包" + "，抢到了" + ChatColor.GOLD + giveMoney + ChatColor.YELLOW + "元" + (type == RedPacketType.JieLongRedPacket ? "，下一个成语的音节是" + ChatColor.UNDERLINE + ChatColor.GREEN + getIdiomPinyin(extraData) : ""));

        amount--;
        getDatabaseManager().update(this);
        if (amount == 0) {
            getRedPacketManager().remove(this);
            broadcastMsg(ChatColor.YELLOW, "玩家" + ChatColor.GOLD + this.player.getName() + ChatColor.YELLOW + "的红包已被抢完，" + ChatColor.GOLD + moneyMap.entrySet().parallelStream().max(Comparator.comparing(Map.Entry::getValue)).map(x -> Bukkit.getServer().getOfflinePlayer(x.getKey()).getName()).orElse("无人") + ChatColor.YELLOW + "是运气王");
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof RedPacket && ((RedPacket) obj).uuid.equals(uuid);
    }

    @Override
    public int hashCode() {
        return 31 * uuid.hashCode();
    }

    @Override
    public String toString() {
        return "红包信息："
                + "\n唯一ID：" + getUUID()
                + "\n发起人：" + player.getName()
                + "\n给予方式：" + giveType.getName()
                + "\n红包类型：" + type.getName()
                + "\n额外信息：" + extraData
                /*
                Convert Milliseconds To LocalDateTime In Java 8
                https://howtoprogram.xyz/2017/02/11/convert-milliseconds-localdatetime-java/
                */
                + "\n过期时间：" + ZonedDateTime.ofInstant(Instant.ofEpochMilli(expireTime), ZoneId.systemDefault()).format(dateTimeFormatter)
                + "\n状态：" + (expired ? "已过期" : amount == 0 ? "已领完" : "等待领取")
                + "\n个数：" + getCurrentAmount() + "/" + getAmount()
                + "\n余额：" + getCurrentMoney() + "/" + getMoney();
    }

    public String toUpdateSQL(String tableName) {
        return String.format("UPDATE %s SET amount=%d,money=%f,extraData='%s',moneyMap='%s',expired=%d where UUID='%s'",
                tableName,
                getCurrentAmount(),
                getCurrentMoney(),
                extraData,
                getGson().toJson(moneyMap, moneyMapType),
                boolToInt(expired),
                uuid.toString());
    }

    /**
     * 生成序列化红包的SQL语句
     *
     * @param tableName 对应的表
     * @return 序列化红包的SQL语句
     */
    public String toInsertSQL(String tableName) {
        //UUID,playerUUID,RedPacketType,giveType,amount,money,moneyMap,extraData,givers,expireTime,Timezone,expired
        return String.format("INSERT INTO %s VALUES ('%s','%s','%s','%s',%d,%f,'%s','%s','%s',%d,'%s',%d)",
                tableName,
                uuid.toString(),
                player.getUniqueId(),
                giveType.name(),
                type.name(),
                getCurrentAmount(),
                getCurrentMoney(),
                getGson().toJson(moneyMap),
                extraData,
                getGson().toJson(givers),
                expireTime,
                ZoneId.systemDefault().getId(),
                boolToInt(expired));
    }

    public enum GiveType {
        LuckyAmount("拼手气"),
        FixAmount("固定数值");
        final String name;

        GiveType(String name) {
            this.name = name;
        }

        String getName() {
            return name;
        }
    }

    public enum RedPacketType {
        CommonRedPacket("普通红包","祝福语"),
        JieLongRedPacket("接龙红包","成语"),
        PasswordRedPacket("口令红包","口令");

        final String name;
        final String extraDataName;

        RedPacketType(String name,String extraDataName) {
            this.name = name;
            this.extraDataName=extraDataName;
        }

        public String getExtraDataName() {
            return extraDataName;
        }

        public String getName() {
            return name;
        }
    }

    /***
     * 生成红包的Builder
     *
     * 参考资料：
     * Java 中的 Builder 模式和协变返回类型
     * http://www.codebelief.com/article/2018/08/java-builder-pattern-and-covariant-return-type/
     *
     * @see RedPacket
     */
    public static class Builder {
        private Map<UUID, Double> moneyMap = new LinkedHashMap<>();
        private OfflinePlayer player;
        private double money;
        private int amount;
        private RedPacketType type = RedPacketType.CommonRedPacket;
        private GiveType givetype = GiveType.LuckyAmount;
        private UUID uuid = UUID.randomUUID();
        //红包有效性检测以及提示信息
        private static final LinkedHashMap<Predicate<Builder>, String> checkMap = new LinkedHashMap<>();
        private boolean expired;
        private Set<UUID> givers=new HashSet<>();
        private ZoneId timeZone=ZoneId.systemDefault();
        //过期时间
        private long expireTime = System.currentTimeMillis() + getInstance().getConfig().getLong("RedPacket.ExpiredTime");

        //初始化
        static {
            checkMap.put((builder) -> builder.amount > 0, "红包数量必须大于0！");
            checkMap.put((builder) -> builder.money > 0, "红包总额必须大于0！");
            checkMap.put((builder) -> getEco().getBalance(builder.player) >= builder.money, "你的余额不足！");
            checkMap.put((builder) -> OperatorHelper.divide(builder.money, builder.amount) >= 0.01, "红包平均最低金额不能低于0.01！");
            checkMap.put((builder) -> builder.type != RedPacketType.PasswordRedPacket || !builder.extraData.contains("§") && !builder.extraData.startsWith("/"), "口令红包不能包含样式代码和命令！");
            checkMap.put((builder) -> builder.type != RedPacketType.JieLongRedPacket || IdiomManager.isValidIdiom(builder.extraData), "该成语无效！");
            checkMap.put((builder) -> getInstance().getConfig().getDouble("RedPacket.MaxMoney") >= builder.money, "红包总额不能超出{MaxMoney}！");
            checkMap.put((builder) -> getInstance().getConfig().getInt("RedPacket.MaxAmount") >= builder.amount, "红包数量不能超出{MaxAmount}！");
            checkMap.put((builder) -> getInstance().getConfig().getDouble("RedPacket.MinMoney") <= builder.money, "红包总额不能小于{MinMoney}！");
        }

        private String extraData = "恭喜发财";


        //内部创建红包方法
        Builder() {
        }

        //外部创建红包方法
        public Builder(Player player) {
            this.player = player;
        }

        public String getExtraDataInfo() {
            return type.getExtraDataName();
        }

        public Builder givers(Set<UUID> givers) {
            this.givers=givers;
            return this;
        }

        Builder expired(boolean expired) {
            this.expired = expired;
            return this;
        }

        Builder expireTime(long expireTime) {
            this.expireTime = expireTime;
            return this;
        }

        public Builder extraData(String extraData) {
            this.extraData = extraData;
            return this;
        }

        public Builder amount(int amount) {
            this.amount = amount;
            return this;
        }

        public Builder money(double money) {
            this.money = money;
            return this;
        }

        public Builder giveType(GiveType givetype) {
            this.givetype = givetype;
            return this;
        }

        public Builder type(RedPacketType type) {
            this.type = type;
            return this;
        }

        Builder player(OfflinePlayer player) {
            this.player = player;
            return this;
        }
        public boolean isValid(){
            String result = checkMap.entrySet().stream().filter(e -> !e.getKey().test(this)).limit(1).map(Map.Entry::getValue).collect(Collectors.joining());
            if (result.isEmpty()) {
                return true;
            } else {
                Matcher matcher = Pattern.compile("\\{?([^{}]+)}+").matcher(result);
                while (matcher.find()) {
                    result = matcher.replaceAll(getInstance().getConfig().getString("RedPacket." + matcher.group(1)));
                }
                sendSimpleMsg(player.getPlayer(), ChatColor.RED, result);
                return false;
            }
        }


        /**
         * 为输入玩家名提供的方法
         * @param giversString 玩家名，多个玩家以,分隔
         */
        public Builder givers(String giversString){
            givers(Arrays.stream(Bukkit.getOfflinePlayers()).filter(player->Arrays.asList(giversString.split(",")).contains(player.getName())).map(OfflinePlayer::getUniqueId).collect(Collectors.toSet()));
            return this;
        }

        Builder uuid(UUID uuid) {
            this.uuid = uuid;
            return this;
        }

        Builder timeZone(ZoneId timeZone) {
            this.timeZone = timeZone;
            return this;
        }

        Builder moneyMap(Map<UUID, Double> map) {
            this.moneyMap=map;
            return this;
        }

        public RedPacket build() {
            return new RedPacket(uuid, player, givetype, type, amount, money, moneyMap, extraData,givers, expireTime,timeZone, expired);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Builder&& ((Builder) obj).uuid.equals(this.uuid);
        }

        @Override
        public int hashCode() {
            return 31*uuid.hashCode();
        }

        public BaseComponent[] getInfo(){
            HoverEvent tipsHoverEvent=new HoverEvent(HoverEvent.Action.SHOW_TEXT,new ComponentBuilder("点击此处来修改该项").create());
            return new ComponentBuilder(ChatColor.GOLD + "要创建的红包信息：\n")
                    .append(ChatColor.GREEN + "红包类型：" + ChatColor.YELLOW + type.getName() + "\n").event(tipsHoverEvent).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/redpacket query type"))
                    .append(ChatColor.GREEN + "给予类型：" + ChatColor.YELLOW + givetype.getName() + "\n").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/redpacket query givetype"))
                    .append(ChatColor.GREEN + "领取人：" + ChatColor.YELLOW + (givers.isEmpty() ? "所有人" : givers.stream().map(Bukkit::getOfflinePlayer).map(OfflinePlayer::getName).collect(Collectors.joining(","))) + "\n").event(tipsHoverEvent).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/redpacket query giver"))
                    .append(ChatColor.GREEN + "金额：" + ChatColor.YELLOW + money + "\n").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/redpacket query money"))
                    .append(ChatColor.GREEN + "个数：" + ChatColor.YELLOW + amount + "\n").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/redpacket query amount"))
                    .append(ChatColor.GREEN + type.getExtraDataName() + "：" + ChatColor.YELLOW + extraData).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/redpacket query extradata"))
                    .append("   ").reset()
                    .append(type == RedPacketType.JieLongRedPacket ? ChatColor.GOLD + "[随机成语]" + "\n" : "\n").event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/redpacket set type jielong")).event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("点击以随机成语").create()))
                    .append(ChatColor.DARK_GREEN + "创建").event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("创建红包").create())).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/redpacket session create"))
                    .append("     ").reset()
                    .append(ChatColor.RED + "取消").event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("取消创建红包").create())).event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/redpacket session cancel")).create();
        }
    }
}
