package sandtechnology.redpacket.util;

import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

import static sandtechnology.redpacket.RedPacketPlugin.getInstance;
import static sandtechnology.redpacket.RedPacketPlugin.log;

//copy and merge form JieLong/src/main/java/top/seraphjack/jielong/idiom
//https://github.com/SeraphJACK/JieLong

/**
 * @author SeraphJACK
 */
public class IdiomManager {
    // 使用线程安全的集合
    private static final Map<String, POJOIdiom> idiomMap = new ConcurrentHashMap<>();
    private static final List<String> idiomList = new CopyOnWriteArrayList<>();

    // 随机数生成器（线程安全）
    private static final Random RANDOM = new Random();

    private IdiomManager() {
    }

    public static void setup() {
        long startTime = System.currentTimeMillis();
        log(Level.INFO, "从Jar中加载成语数据库(idiom.json)...");

        InputStream fis = getInstance().getClass().getClassLoader().getResourceAsStream("idiom.json");
        if (fis == null) {
            log(Level.SEVERE, "idiom.json丢失！将无法进行成语接龙！");
            throw new RuntimeException("idiom.json丢失！将无法进行成语接龙！");
        }

        loadIdiomData(fis);

        log(Level.INFO, "成语数据库载入完成，花费了" + (System.currentTimeMillis() - startTime) + "毫秒。");
    }

    private static void loadIdiomData(InputStream inputStream) {
        try {
            Gson gson = new Gson();
            POJOIdiom[] idioms = gson.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), POJOIdiom[].class);

            // 清空现有数据
            idiomMap.clear();
            idiomList.clear();

            // 使用并行流提高加载速度
            Arrays.stream(idioms).parallel().forEach(i -> idiomMap.put(i.word, i));

            // 添加所有成语到列表
            idiomList.addAll(idiomMap.keySet());

            log(Level.INFO, "加载了 " + idiomMap.size() + " 个成语");
        } catch (Exception e) {
            log(Level.SEVERE, "加载成语数据时发生错误: " + e.getMessage());
            throw new RuntimeException("无法加载成语数据！", e);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception e) {
                // 忽略关闭异常
            }
        }
    }

    public static void reload() {
        // 重新加载时，先清空数据
        idiomMap.clear();
        idiomList.clear();
        setup();
    }

    public static boolean isValidIdiom(String idiom) {
        return idiomMap.containsKey(idiom);
    }

    public static String getRandomIdiom() {
        if (idiomList.isEmpty()) {
            return null;
        }

        int randomIndex = RANDOM.nextInt(idiomList.size());
        return idiomList.get(randomIndex);
    }

    public static List<String> getRandomIdioms(int count) {
        if (idiomList.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }

        int actualCount = Math.min(count, idiomList.size());
        List<String> result = new ArrayList<>(actualCount);

        for (int i = 0; i < actualCount; i++) {
            int randomIndex = RANDOM.nextInt(idiomList.size());
            result.add(idiomList.get(randomIndex));
        }

        return result;
    }

    public static boolean isValidSequence(String former, String idiom) {
        if (!(isValidIdiom(former) && isValidIdiom(idiom))) {
            return false;
        } else {
            return Objects.equals(getLastPinyin(idiomMap.get(former).pinyin), getFirstPinyin(idiomMap.get(idiom).pinyin));
        }
    }

    private static String removeRedundantCharacters(String in) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < in.length(); i++) {
            //                    | Magic value
            if (in.charAt(i) <= 500) {
                sb.append(in.charAt(i));
            }
        }
        return sb.toString();
    }

    private static String getLastPinyin(String pinyin) {
        for (int i = pinyin.length() - 1; i > 0; i--) {
            if (pinyin.charAt(i) == ' ') {
                return removeRedundantCharacters(Normalizer.normalize(pinyin.substring(i + 1), Normalizer.Form.NFKD));
            }
        }
        return null;
    }

    private static String getFirstPinyin(String pinyin) {
        for (int i = 0; i < pinyin.length(); i++) {
            if (pinyin.charAt(i) == ' ') {
                return removeRedundantCharacters(Normalizer.normalize(pinyin.substring(0, i), Normalizer.Form.NFKD));
            }
        }
        return null;
    }

    public static String getIdiomPinyin(String idiom) {
        return idiomMap.containsKey(idiom) ? getLastPinyin(idiomMap.get(idiom).pinyin) : "无";
    }

    public static Optional<IdiomDetails> getIdiomDetails(String idiom) {
        POJOIdiom pojoIdiom = idiomMap.get(idiom);
        if (pojoIdiom == null) {
            return Optional.empty();
        }

        return Optional.of(new IdiomDetails(
                pojoIdiom.word,
                pojoIdiom.pinyin,
                pojoIdiom.explanation,
                pojoIdiom.derivation,
                pojoIdiom.example,
                pojoIdiom.abbreviation
        ));
    }

    public static List<String> searchIdioms(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String searchTerm = keyword.toLowerCase().trim();
        List<String> results = new ArrayList<>();

        for (String idiom : idiomList) {
            if (idiom.contains(searchTerm)) {
                results.add(idiom);
            }
        }

        return results;
    }

    public static int getIdiomCount() {
        return idiomList.size();
    }

    private static class POJOIdiom {
        String derivation, example, explanation, pinyin, abbreviation, word;
    }

    public static class IdiomDetails {
        private final String word;
        private final String pinyin;
        private final String explanation;
        private final String derivation;
        private final String example;
        private final String abbreviation;

        public IdiomDetails(String word, String pinyin, String explanation,
                            String derivation, String example, String abbreviation) {
            this.word = word;
            this.pinyin = pinyin;
            this.explanation = explanation;
            this.derivation = derivation;
            this.example = example;
            this.abbreviation = abbreviation;
        }

        public String getWord() { return word; }
        public String getPinyin() { return pinyin; }
        public String getExplanation() { return explanation; }
        public String getDerivation() { return derivation; }
        public String getExample() { return example; }
        public String getAbbreviation() { return abbreviation; }

        @Override
        public String toString() {
            return String.format("成语: %s\n拼音: %s\n解释: %s\n出处: %s\n例子: %s\n缩写: %s",
                    word, pinyin, explanation, derivation, example, abbreviation);
        }
    }
}