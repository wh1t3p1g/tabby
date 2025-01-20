package tabby.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tabby.common.utils.FileUtils;
import tabby.common.utils.TickTock;
import tabby.core.container.RulesContainer;

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
    public static String JRE_LIBS_PATH = String.join(File.separator, System.getProperty("user.dir"), "jre_libs");
    public static String TEMP_PATH = String.join(File.separator, System.getProperty("user.dir"), "temp");
    public static String RULES_PATH;
    public static String SINK_RULE_PATH;
    public static String SYSTEM_RULE_PATH;
    public static String INC_RULE_PATH;
    public static String IGNORE_PATH;
    public static String TAG_RULE_PATH;
    public static String XML_RULE_PATH;
    public static String BASIC_CLASSES_PATH;
    public static String COMMON_JARS_PATH;
    public static String CLASSES_OUTPUT_PATH;
    public static String METHODS_OUTPUT_PATH;
    public static String CALL_RELATIONSHIP_OUTPUT_PATH;
    public static String ALIAS_RELATIONSHIP_OUTPUT_PATH;
    public static String EXTEND_RELATIONSHIP_OUTPUT_PATH;
    public static String HAS_RELATIONSHIP_OUTPUT_PATH;
    public static String INTERFACE_RELATIONSHIP_OUTPUT_PATH;

    public static boolean DEBUG = false;
    public static boolean PRINT_METHODS = false;
    public static boolean INTER_PROCEDURAL = false;
    public static boolean TIMEOUT_FORCE_STOP = false;
    public static int TIMEOUT = 2;
    public static int METHOD_TIMEOUT = 10;
    public static long METHOD_TIMEOUT_SECONDS = 600;
    public static String MODE = "gadget";
    public static String TARGET = null;
    public static String OUTPUT_DIRECTORY = "";
    public static int ALIAS_METHOD_MAX_COUNT = 5;
    public static int ARRAY_MAX_LENGTH = 25;
    public static int METHOD_MAX_DEPTH = 300;
    public static int METHOD_MAX_BODY_COUNT = 5000;
    public static int OBJECT_MAX_TRIGGER_TIMES = 500;
    public static int OBJECT_FIELD_K_LIMIT = 10;
    public static String THREAD_POOL_SIZE = "max";
    public static RulesContainer rulesContainer;

    public static Map<String, String> libraries = new HashMap<>();

    public static boolean IS_WEB_MODE = false;
    public static boolean IS_JDK_ONLY = false;
    public static boolean IS_JDK_PROCESS = false;
    public static boolean IS_WITH_ALL_JDK = false;
    public static boolean IS_CHECK_FAT_JAR = false;
    public static boolean IS_BUILD_WIH_CACHE_ENABLE = false;
    public static boolean IS_PRIM_TYPE_NEED_TO_CREATE = false;
    public static boolean IS_NEED_TO_CREATE_IGNORE_LIST = true;
    public static boolean IS_NEED_TO_DEAL_NEW_ADDED_METHOD = true;
    public static boolean IS_NEED_REMOVE_NOT_POLLUTED_CALL_SITE = false;
    public static boolean IS_ON_DEMAND_DRIVE = false;
    public static boolean IS_NEED_PROCESS_XML = false;
    public static boolean IS_NEED_ADD_TO_TIMEOUT_LIST = true;
    public static boolean IS_NEED_ANALYSIS_EVERYTHING = false;
    public static boolean IS_JRE9_MODULE = false;
    public static boolean IS_USING_SETTING_JRE = false;
    public static String TARGET_JAVA_HOME = null;
    public static boolean cleanStatus = false;
    public static boolean isInitialed = false;
    private static Properties props;

    public static TickTock tickTock;
    public static ThreadPoolTaskExecutor tabbyCollectorExecutor;
    public static ThreadPoolTaskExecutor tabbySaverExecutor;
    public static boolean GLOBAL_FORCE_STOP = false;
    public final static String DEBUG_METHOD_SIGNATURE = "";

    public static void init() {
        if (props == null) {
            props = new Properties();
            // read from config/settings.properties
            try (Reader reader = new FileReader(CONFIG_FILE_PATH)) {
                props.load(reader);
            } catch (IOException e) {
                throw new IllegalArgumentException("Config ERROR: settings.properties file not found!");
            }
            // resolve rule directory
            RULES_PATH = getProperty("tabby.build.rules.directory", "./rules", props);
            RULES_PATH = FileUtils.getRealPath(RULES_PATH);

            SINK_RULE_PATH = String.join(File.separator, RULES_PATH, "sinks.json");
            SYSTEM_RULE_PATH = String.join(File.separator, RULES_PATH, "system.json");
            INC_RULE_PATH = String.join(File.separator, RULES_PATH, "inc.json");
            IGNORE_PATH = String.join(File.separator, RULES_PATH, "ignores.json");
            TAG_RULE_PATH = String.join(File.separator, RULES_PATH, "tags.json");
            XML_RULE_PATH = String.join(File.separator, RULES_PATH, "xmlRules.json");
            BASIC_CLASSES_PATH = String.join(File.separator, RULES_PATH, "basicClasses.json");
            COMMON_JARS_PATH = String.join(File.separator, RULES_PATH, "commonJars.json");
            IS_NEED_PROCESS_XML = getBooleanProperty("tabby.build.isNeedToProcessXml", "false", props);
            IS_JRE9_MODULE = getBooleanProperty("tabby.build.isJRE9Module", "false", props);
            IS_USING_SETTING_JRE = getBooleanProperty("tabby.build.useSettingJRE", "false", props);
            TARGET_JAVA_HOME = getProperty("tabby.build.javaHome", null, props);
            THREAD_POOL_SIZE = getProperty("tabby.build.thread.size", "max", props);

            int maxThreadPoolSize = Runtime.getRuntime().availableProcessors();
            if ("max".equals(THREAD_POOL_SIZE)) {
                AsyncConfiguration.CORE_POOL_SIZE = maxThreadPoolSize;
            } else if ("env".equals(THREAD_POOL_SIZE)) {
                String env = System.getenv("TABBY_BUILD_THREAD_SIZE");
                try {
                    AsyncConfiguration.CORE_POOL_SIZE = Math.min(Integer.parseInt(env), maxThreadPoolSize);
                } catch (Exception e) {
                    // 解析出错 使用最大的线程数
                    AsyncConfiguration.CORE_POOL_SIZE = maxThreadPoolSize;
                }
            } else {
                try {
                    AsyncConfiguration.CORE_POOL_SIZE = Math.min(Integer.parseInt(THREAD_POOL_SIZE), maxThreadPoolSize);
                } catch (Exception e) {
                    // 解析出错 使用最大的线程数
                    AsyncConfiguration.CORE_POOL_SIZE = maxThreadPoolSize;
                }
            }
        }
    }

    public static void initConfig() {
        if (isInitialed) return;
//        log.info("Try to apply settings.properties");
        init();

        // apply to GlobalConfiguration
        MODE = getProperty("tabby.build.mode", "gadget", props);
        TARGET = getProperty("tabby.build.target", "", props);

        OUTPUT_DIRECTORY = getProperty("tabby.output.directory", "./output", props);

        if (!FileUtils.fileExists(OUTPUT_DIRECTORY)) {
            FileUtils.createDirectory(OUTPUT_DIRECTORY);
        } else {
            // 如果存在output，则删除该目录下的csv文件
            clean(OUTPUT_DIRECTORY);
        }

        if (IS_USING_SETTING_JRE && !FileUtils.fileExists(JRE_LIBS_PATH)) {
            FileUtils.createDirectory(JRE_LIBS_PATH);
        }

        // resolve cache directory
        OUTPUT_DIRECTORY = FileUtils.getRealPath(OUTPUT_DIRECTORY);

        CLASSES_OUTPUT_PATH = String.join(File.separator, OUTPUT_DIRECTORY, "GRAPHDB_PUBLIC_CLASSES.csv");
        METHODS_OUTPUT_PATH = String.join(File.separator, OUTPUT_DIRECTORY, "GRAPHDB_PUBLIC_METHODS.csv");
        CALL_RELATIONSHIP_OUTPUT_PATH = String.join(File.separator, OUTPUT_DIRECTORY, "GRAPHDB_PUBLIC_CALL.csv");
        ALIAS_RELATIONSHIP_OUTPUT_PATH = String.join(File.separator, OUTPUT_DIRECTORY, "GRAPHDB_PUBLIC_ALIAS.csv");
        EXTEND_RELATIONSHIP_OUTPUT_PATH = String.join(File.separator, OUTPUT_DIRECTORY, "GRAPHDB_PUBLIC_EXTEND.csv");
        HAS_RELATIONSHIP_OUTPUT_PATH = String.join(File.separator, OUTPUT_DIRECTORY, "GRAPHDB_PUBLIC_HAS.csv");
        INTERFACE_RELATIONSHIP_OUTPUT_PATH = String.join(File.separator, OUTPUT_DIRECTORY, "GRAPHDB_PUBLIC_INTERFACES.csv");

        DEBUG = getBooleanProperty("tabby.debug.details", "false", props);
        PRINT_METHODS = getBooleanProperty("tabby.debug.print.current.methods", "false", props);
        INTER_PROCEDURAL = getBooleanProperty("tabby.build.interProcedural", "false", props);
        TIMEOUT_FORCE_STOP = getBooleanProperty("tabby.build.timeout.forceStop", "true", props);

        IS_WEB_MODE = "web".equals(MODE);
        IS_JDK_ONLY = getBooleanProperty("tabby.build.isJDKOnly", "false", props);

        if (IS_JDK_ONLY) {
            IS_WITH_ALL_JDK = true;
            IS_JDK_PROCESS = true;
        } else {
            IS_WITH_ALL_JDK = getBooleanProperty("tabby.build.withAllJDK", "false", props);
            IS_JDK_PROCESS = getBooleanProperty("tabby.build.isJDKProcess", "false", props);
        }

        if (IS_WITH_ALL_JDK) {
            IS_JDK_PROCESS = true;
        }

        IS_CHECK_FAT_JAR = getBooleanProperty("tabby.build.checkFatJar", "false", props);
        IS_BUILD_WIH_CACHE_ENABLE = getBooleanProperty("tabby.build.with.cache.enable", "false", props);
        IS_PRIM_TYPE_NEED_TO_CREATE = getBooleanProperty("tabby.build.isPrimTypeNeedToCreate", "false", props);
        IS_NEED_REMOVE_NOT_POLLUTED_CALL_SITE = getBooleanProperty("tabby.build.removeNotPollutedCallSite", "false", props);
        IS_NEED_TO_CREATE_IGNORE_LIST = getBooleanProperty("tabby.build.isNeedToCreateIgnoreList", "true", props);
        IS_NEED_TO_DEAL_NEW_ADDED_METHOD = getBooleanProperty("tabby.build.isNeedToDealNewAddedMethod", "true", props);
        IS_ON_DEMAND_DRIVE = getBooleanProperty("tabby.build.onDemandDrive", "false", props);
        IS_NEED_ANALYSIS_EVERYTHING = getBooleanProperty("tabby.build.analysis.everything", "false", props);

        try {
            TIMEOUT = getIntProperty("tabby.build.thread.timeout", "2", props);
        } catch (Exception ignore) {
        }

        try {
            ALIAS_METHOD_MAX_COUNT = getIntProperty("tabby.build.alias.maxCount", "5", props);
        } catch (Exception ignore) {
        }

        try {
            ARRAY_MAX_LENGTH = getIntProperty("tabby.build.array.maxLength", "25", props);
        } catch (Exception ignore) {
        }

        try {
            METHOD_MAX_DEPTH = getIntProperty("tabby.build.method.maxDepth", "300", props);
        } catch (Exception ignore) {
        }

        try {
            METHOD_TIMEOUT = getIntProperty("tabby.build.method.timeout", "10", props);
            METHOD_TIMEOUT_SECONDS = METHOD_TIMEOUT * 60L;
        } catch (Exception ignore) {
        }

        try {
            METHOD_MAX_BODY_COUNT = getIntProperty("tabby.build.method.maxBodyCount", "5000", props);
        } catch (Exception ignore) {
        }

        try {
            OBJECT_MAX_TRIGGER_TIMES = getIntProperty("tabby.build.object.maxTriggerTimes", "500", props);
        } catch (Exception ignore) {
        }

        try {
            OBJECT_FIELD_K_LIMIT = getIntProperty("tabby.build.object.field.k.limit", "10", props);
        } catch (Exception ignore) {
        }

        // 支持绝对路径 issue 7
        if (!IS_JDK_ONLY && TARGET != null && !FileUtils.fileExists(TARGET)) {
            String target = String.join(File.separator, System.getProperty("user.dir"), TARGET);
            if (!FileUtils.fileExists(target)) {
                throw new IllegalArgumentException("target not exists!");
            }
        }

        String libraries = props.getProperty("tabby.build.libraries");
        if (libraries != null) {
            if (FileUtils.fileExists(libraries)) {
                LIBS_PATH = libraries;
            } else {
                libraries = String.join(File.separator, System.getProperty("user.dir"), libraries);
                if (FileUtils.fileExists(libraries)) {
                    LIBS_PATH = libraries;
                }
            }
        }

        isInitialed = true;
    }

    public static void clean(String directory) {
        try {
            File cacheDir = new File(directory);
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    String name = file.getName();
                    if (name.endsWith(".csv") || name.endsWith(".db")) {
                        Files.deleteIfExists(file.toPath());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getProperty(String key, String defaultValue, Properties props) {
        String data = props.getProperty(key, defaultValue);
        if (data != null) {
            return data.trim();
        }
        return null;
    }

    public static boolean getBooleanProperty(String key, String defaultValue, Properties props) {
        return Boolean.parseBoolean(getProperty(key, defaultValue, props));
    }

    public static int getIntProperty(String key, String defaultValue, Properties props) {
        return Integer.parseInt(getProperty(key, defaultValue, props));
    }

}
