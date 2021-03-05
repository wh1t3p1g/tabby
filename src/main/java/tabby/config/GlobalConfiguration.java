package tabby.config;

import com.google.gson.Gson;

import java.io.File;

/**
 * @author wh1t3P1g
 * @since 2020/11/9
 */
public class GlobalConfiguration {

    public static String RULES_PATH = String.join(File.separator, System.getProperty("user.dir"), "rules");
    public static String KNOWLEDGE_PATH = String.join(File.separator, RULES_PATH, "knowledges.json");
    public static String IGNORE_PATH = String.join(File.separator, RULES_PATH, "ignores.json");
    public static String BASIC_CLASSES_PATH = String.join(File.separator, RULES_PATH, "basicClasses.json");
    public static String CACHE_PATH = String.join(File.separator, System.getProperty("user.dir"), "cache");
    public static String CLASSES_CACHE_PATH = String.join(File.separator,CACHE_PATH, "GRAPHDB_PUBLIC_CLASSES.csv");
    public static String METHODS_CACHE_PATH = String.join(File.separator,CACHE_PATH, "GRAPHDB_PUBLIC_METHODS.csv");
    public static String CALL_RELATIONSHIP_CACHE_PATH = String.join(File.separator,CACHE_PATH, "GRAPHDB_PUBLIC_CALL.csv");
    public static String ALIAS_RELATIONSHIP_CACHE_PATH = String.join(File.separator,CACHE_PATH, "GRAPHDB_PUBLIC_ALIAS.csv");
    public static String EXTEND_RELATIONSHIP_CACHE_PATH = String.join(File.separator,CACHE_PATH, "GRAPHDB_PUBLIC_EXTEND.csv");
    public static String HAS_RELATIONSHIP_CACHE_PATH = String.join(File.separator,CACHE_PATH, "GRAPHDB_PUBLIC_HAS.csv");
    public static String INTERFACE_RELATIONSHIP_CACHE_PATH = String.join(File.separator,CACHE_PATH, "GRAPHDB_PUBLIC_INTERFACES.csv");

    public static Gson GSON = new Gson();
    public static boolean DEBUG = false;

}
