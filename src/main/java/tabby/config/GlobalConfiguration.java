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
    public static String DATABASE_PATH = String.join(File.separator, System.getProperty("user.dir"), "graphdb.mv.db");
    public static String DATABASE_TRACE_PATH = String.join(File.separator, System.getProperty("user.dir"), "graphdb.trace.db");
    public static String KNOWLEDGE_PATH = String.join(File.separator, RULES_PATH, "knowledges.json");
    public static String IGNORE_PATH = String.join(File.separator, RULES_PATH, "ignore.json");
    public static String CACHE_PATH = String.join(File.separator, System.getProperty("user.dir"), "docker", "cache");
    public static String RUNTIME_CACHE_PATH = String.join(File.separator, CACHE_PATH, "runtime.json");
    public static String CLASSES_CACHE_PATH = String.join(File.separator,CACHE_PATH, "GRAPHDB_PUBLIC_CLASSES.csv");
    public static String METHODS_CACHE_PATH = String.join(File.separator,CACHE_PATH, "GRAPHDB_PUBLIC_METHODS.csv");
    public static String CALL_RELATIONSHIP_CACHE_PATH = String.join(File.separator,CACHE_PATH, "GRAPHDB_PUBLIC_CALL.csv");
    public static String ALIAS_RELATIONSHIP_CACHE_PATH = String.join(File.separator,CACHE_PATH, "GRAPHDB_PUBLIC_ALIAS.csv");
    public static String EXTEND_RELATIONSHIP_CACHE_PATH = String.join(File.separator,CACHE_PATH, "GRAPHDB_PUBLIC_EXTEND.csv");
    public static String HAS_RELATIONSHIP_CACHE_PATH = String.join(File.separator,CACHE_PATH, "GRAPHDB_PUBLIC_HAS.csv");
    public static String INTERFACE_RELATIONSHIP_CACHE_PATH = String.join(File.separator,CACHE_PATH, "GRAPHDB_PUBLIC_INTERFACES.csv");
    public static List<String[]> CSV_HEADERS = new ArrayList<>(Arrays.asList(
            new String[]{"uuid", "name", "superClass", "interfaces", "isInterface", "hasSuperClass", "hasInterfaces", "fields"},// class
            new String[]{"uuid", "name", "signature", "subSignature", "modifiers", "isStatic", "hasParameters", "isSink", "isSource", "isPolluted", "parameters", "actions", "pollutedPosition", "returnType"},// method
            new String[]{"uuid", "source", "target"}, // extend/interfaces/
            new String[]{"uuid", "classRef", "MethodRef"}, // has
            new String[]{"uuid", "source", "target", "lineNum", "isPolluted", "pollutedPosition", "realCallType", "invokerType"}, // call
            new String[]{"uuid", "source", "target"} // alias

    ));

    public static Gson GSON = new Gson();
}
