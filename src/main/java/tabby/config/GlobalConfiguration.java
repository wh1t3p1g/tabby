package tabby.config;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import tabby.core.container.RulesContainer;
import tabby.util.FileUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author wh1t3P1g
 * @since 2020/11/9
 */
@Slf4j
public class GlobalConfiguration {

    public static String CONFIG_FILE_PATH = String.join(File.separator, System.getProperty("user.dir"), "config", "settings.properties");
    public static String LIBS_PATH = String.join(File.separator, System.getProperty("user.dir"), "libs");
    public static String RULES_PATH;
    public static String SINK_RULE_PATH;
    public static String SYSTEM_RULE_PATH;
    public static String IGNORE_PATH;
    public static String BASIC_CLASSES_PATH;
    public static String COMMON_JARS_PATH;
    public static String CLASSES_OUTPUT_PATH;
    public static String METHODS_OUTPUT_PATH;
    public static String CALL_RELATIONSHIP_OUTPUT_PATH;
    public static String ALIAS_RELATIONSHIP_OUTPUT_PATH;
    public static String EXTEND_RELATIONSHIP_OUTPUT_PATH;
    public static String HAS_RELATIONSHIP_OUTPUT_PATH;
    public static String INTERFACE_RELATIONSHIP_OUTPUT_PATH;
    public static boolean IS_DOCKER_IMPORT_PATH = false;
    public static Gson GSON = new Gson();
    public static boolean DEBUG = false;
    public static int TIMEOUT = 2;
    public static String MODE = "gadget";
    public static String TARGET = null;
    public static String OUTPUT_DIRECTORY = "";
    public static String NEO4J_USERNAME = null;
    public static String NEO4J_PASSWORD = null;
    public static String NEO4J_URL = null;

    public static RulesContainer rulesContainer;

    public static Map<String, String> libraries = new HashMap<>();

    public static boolean IS_WEB_MODE = false;
    public static boolean IS_JDK_ONLY = false;
    public static boolean IS_JDK_PROCESS = false;
    public static boolean IS_BUILD_ENABLE = false;
    public static boolean IS_LOAD_ENABLE = false;
    public static boolean IS_EXCLUDE_JDK = false;
    public static boolean IS_WITH_ALL_JDK = false;
    public static boolean IS_CHECK_FAT_JAR = false;
    public static boolean IS_FULL_CALL_GRAPH_CONSTRUCT = false;
    public static boolean IS_NEED_TO_CREATE_IGNORE_LIST = true;
    private static Properties props;
    public static boolean isInitialed = false;
    public static boolean isNeedStop = false;

    public static String THREAD_POOL_SIZE = "max";

    public static void init(){
        if(props == null){
            props = new Properties();
            // read from config/settings.properties
            try(Reader reader = new FileReader(CONFIG_FILE_PATH)){
                props.load(reader);
            } catch (IOException e) {
                throw new IllegalArgumentException("Config ERROR: settings.properties file not found!");
            }
            // db settings
            NEO4J_USERNAME = getProperty("tabby.neo4j.username", "neo4j", props);
            NEO4J_PASSWORD = getProperty("tabby.neo4j.password", "neo4j", props);
            NEO4J_URL = getProperty("tabby.neo4j.url", "bolt://localhost:7687", props);
            // resolve rule directory
            RULES_PATH = getProperty("tabby.build.rules.directory", "./rules", props);
            RULES_PATH = FileUtils.getRealPath(RULES_PATH);

            SINK_RULE_PATH = String.join(File.separator, RULES_PATH, "sinks.json");
            SYSTEM_RULE_PATH = String.join(File.separator, RULES_PATH, "system.json");
            IGNORE_PATH = String.join(File.separator, RULES_PATH, "ignores.json");
            BASIC_CLASSES_PATH = String.join(File.separator, RULES_PATH, "basicClasses.json");
            COMMON_JARS_PATH = String.join(File.separator, RULES_PATH, "commonJars.json");
            THREAD_POOL_SIZE = getProperty("tabby.build.thread.size", "max", props);

            int maxThreadPoolSize = Runtime.getRuntime().availableProcessors();
            if("max".equals(THREAD_POOL_SIZE)){
                AsyncConfiguration.CORE_POOL_SIZE = maxThreadPoolSize;
            } else{
                try{
                    AsyncConfiguration.CORE_POOL_SIZE = Math.min(Integer.parseInt(THREAD_POOL_SIZE), maxThreadPoolSize);
                }catch (Exception e){
                    // 解析出错 使用最大的线程数
                    AsyncConfiguration.CORE_POOL_SIZE = maxThreadPoolSize;
                }
            }
        }
    }

    public static void initConfig(){
        if(isInitialed) return;
//        log.info("Try to apply settings.properties");
        init();
        // apply others
        MODE = getProperty("tabby.build.mode", "gadget", props);
        TARGET = getProperty("tabby.build.target", "", props);

        OUTPUT_DIRECTORY = getProperty("tabby.output.directory", "./output", props);

        if(!FileUtils.fileExists(OUTPUT_DIRECTORY)){
            FileUtils.createDirectory(OUTPUT_DIRECTORY);
        }else{
            // 如果存在output，则删除该目录下的csv文件
            clean(OUTPUT_DIRECTORY);
        }

        // resolve cache directory
        OUTPUT_DIRECTORY = FileUtils.getRealPath(OUTPUT_DIRECTORY);

        CLASSES_OUTPUT_PATH = String.join(File.separator,OUTPUT_DIRECTORY, "GRAPHDB_PUBLIC_CLASSES.csv");
        METHODS_OUTPUT_PATH = String.join(File.separator,OUTPUT_DIRECTORY, "GRAPHDB_PUBLIC_METHODS.csv");
        CALL_RELATIONSHIP_OUTPUT_PATH = String.join(File.separator,OUTPUT_DIRECTORY, "GRAPHDB_PUBLIC_CALL.csv");
        ALIAS_RELATIONSHIP_OUTPUT_PATH = String.join(File.separator,OUTPUT_DIRECTORY, "GRAPHDB_PUBLIC_ALIAS.csv");
        EXTEND_RELATIONSHIP_OUTPUT_PATH = String.join(File.separator,OUTPUT_DIRECTORY, "GRAPHDB_PUBLIC_EXTEND.csv");
        HAS_RELATIONSHIP_OUTPUT_PATH = String.join(File.separator,OUTPUT_DIRECTORY, "GRAPHDB_PUBLIC_HAS.csv");
        INTERFACE_RELATIONSHIP_OUTPUT_PATH = String.join(File.separator,OUTPUT_DIRECTORY, "GRAPHDB_PUBLIC_INTERFACES.csv");

        IS_LOAD_ENABLE = getBooleanProperty("tabby.load.enable", "false", props);
        IS_BUILD_ENABLE = getBooleanProperty("tabby.build.enable", "false", props);
        IS_DOCKER_IMPORT_PATH = getBooleanProperty("tabby.cache.isDockerImportPath", "false", props);

        DEBUG = getBooleanProperty("tabby.debug.details", "false", props);

        IS_WEB_MODE = "web".equals(MODE);
        IS_JDK_ONLY = getBooleanProperty("tabby.build.isJDKOnly", "false", props);

        if(IS_JDK_ONLY){
            IS_WITH_ALL_JDK = true;
            IS_EXCLUDE_JDK = false;
            IS_JDK_PROCESS = true;
        }else{
            IS_WITH_ALL_JDK = getBooleanProperty("tabby.build.withAllJDK", "false", props);
            IS_EXCLUDE_JDK = getBooleanProperty("tabby.build.excludeJDK", "false", props);
            IS_JDK_PROCESS = getBooleanProperty("tabby.build.isJDKProcess", "false", props);
        }

        IS_CHECK_FAT_JAR = getBooleanProperty("tabby.build.checkFatJar", "false", props);
        IS_FULL_CALL_GRAPH_CONSTRUCT = getBooleanProperty("tabby.build.isFullCallGraphCreate", "false", props);
        IS_NEED_TO_CREATE_IGNORE_LIST = getBooleanProperty("tabby.build.isNeedToCreateIgnoreList", "true", props);

        try{
            TIMEOUT = getIntProperty("tabby.build.thread.timeout", "2", props);
        }catch (Exception ignore){
        }

        // 支持绝对路径 issue 7
        if(!IS_JDK_ONLY && TARGET != null && !FileUtils.fileExists(TARGET)){
            String target = String.join(File.separator, System.getProperty("user.dir"), TARGET);
            if(!FileUtils.fileExists(target)){
                throw new IllegalArgumentException("target not exists!");
            }
        }

        String libraries = props.getProperty("tabby.build.libraries");
        if(libraries != null){
            if(FileUtils.fileExists(libraries)){
                LIBS_PATH = libraries;
            }else{
                libraries = String.join(File.separator, System.getProperty("user.dir"), libraries);
                if(FileUtils.fileExists(libraries)){
                    LIBS_PATH = libraries;
                }
            }
        }

        isInitialed = true;
    }

    public static void clean(String directory){
        try {
            File cacheDir = new File(directory);
            File[] files = cacheDir.listFiles();
            if(files != null){
                for(File file: files){
                    String name = file.getName();
                    if(name.endsWith(".csv") || name.endsWith(".db")){
                        Files.deleteIfExists(file.toPath());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getProperty(String key, String defaultValue, Properties props){
        return props.getProperty(key, defaultValue).trim();
    }

    public static boolean getBooleanProperty(String key, String defaultValue, Properties props){
        return Boolean.parseBoolean(getProperty(key, defaultValue, props));
    }

    public static int getIntProperty(String key, String defaultValue, Properties props){
        return Integer.parseInt(getProperty(key, defaultValue, props));
    }

}
