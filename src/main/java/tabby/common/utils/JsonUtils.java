package tabby.common.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.lang.reflect.Type;

/**
 * 为了解决 csv "" \" 同时存在时，可能导致的解析报错
 * @author wh1t3p1g
 * @project tabby
 * @since 2024/3/23
 */
public class JsonUtils {

    public static Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static String toJson(Object obj){
        return GSON.toJson(obj);
    }

    public static  <T> T fromJson(String json, Type typeOfT) throws JsonSyntaxException {
        return GSON.fromJson(json, typeOfT);
    }

    /**
     * 为了解决 csv "" \" 同时存在时，可能导致的解析报错
     * @param obj
     * @return
     */
    public static String toJsonWithReplace(Object obj){
        String data = GSON.toJson(obj);
        return data.replace("\\", "\\\\");
    }

    public static  <T> T fromJsonWithReplace(String json, Type typeOfT) throws JsonSyntaxException {
        json = json.replace("\\\\", "\\");
        return GSON.fromJson(json, typeOfT);
    }
}
