package sandtechnology.redpacket.util;

import com.google.gson.Gson;

/**
 * 返回Gson实例的工具类
 */
public class JsonHelper {
    private static final Gson gson = new Gson();

    private JsonHelper() {
    }

    public static Gson getGson() {
        return gson;
    }
}
