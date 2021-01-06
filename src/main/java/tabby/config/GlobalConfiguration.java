package tabby.config;

import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author wh1t3P1g
 * @since 2020/11/9
 */
public class GlobalConfiguration {

    public static String RULES_PATH = String.join(File.separator, System.getProperty("user.dir"), "rules");
    public static String KNOWLEDGE_PATH = String.join(File.separator, RULES_PATH, "knowledges.json");
    public static String CACHE_PATH = String.join(File.separator, System.getProperty("user.dir"), "docker", "cache");
    public static String RUNTIME_CACHE_PATH = String.join(File.separator, CACHE_PATH, "runtime.json");
    public static String CLASSES_CACHE_PATH = String.join(File.separator,CACHE_PATH, "classes.csv");
    public static String METHODS_CACHE_PATH = String.join(File.separator,CACHE_PATH, "methods.csv");
    public static String CALL_RELATIONSHIP_CACHE_PATH = String.join(File.separator,CACHE_PATH, "calls.csv");
    public static String ALIAS_RELATIONSHIP_CACHE_PATH = String.join(File.separator,CACHE_PATH, "aliases.csv");
    public static String EXTEND_RELATIONSHIP_CACHE_PATH = String.join(File.separator,CACHE_PATH, "extends.csv");
    public static String HAS_RELATIONSHIP_CACHE_PATH = String.join(File.separator,CACHE_PATH, "has.csv");
    public static String INTERFACE_RELATIONSHIP_CACHE_PATH = String.join(File.separator,CACHE_PATH, "interfaces.csv");
    public static List<String[]> CSV_HEADERS = new ArrayList<>(Arrays.asList(
            new String[]{"uuid", "name", "superClass", "interfaces", "isInterface", "hasSuperClass", "hasInterfaces", "fields"},// class
            new String[]{"uuid", "name", "signature", "subSignature", "modifiers", "isStatic", "hasParameters", "isSink", "isSource", "isPolluted", "parameters", "actions", "pollutedPosition", "returnType"},// method
            new String[]{"uuid", "source", "target"}, // extend/interfaces/
            new String[]{"uuid", "classRef", "MethodRef"}, // has
            new String[]{"uuid", "source", "target", "lineNum", "isPolluted", "pollutedPosition", "realCallType", "invokerType"}, // call
            new String[]{"uuid", "source", "target", "isPolluted"} // alias

    ));

    public static Gson GSON = new Gson();
}
