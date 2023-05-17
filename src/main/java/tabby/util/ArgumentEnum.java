package tabby.util;

/**
 * @author wh1t3p1g
 * @since 2021/8/31
 */
public enum ArgumentEnum {
    IS_JDK_PROCESS("tabby.build.isJDKProcess"),
    WITH_ALL_JDK("tabby.build.withAllJDK"),
    EXCLUDE_JDK("tabby.build.excludeJDK"),
    BUILD_ENABLE("tabby.build.enable"),
    BUILD_THREADS("tabby.build.threads"),
    TARGET("tabby.build.target"),
    LIBRARIES("tabby.build.libraries"),
    IS_JDK_ONLY("tabby.build.isJDKOnly"),
    LOAD_ENABLE("tabby.load.enable"),
    CHECK_FAT_JAR("tabby.build.checkFatJar"),
    SET_PTA_ENABLE("tabby.build.pta"),
    SET_DEBUG_ENABLE("tabby.debug.details"),
    SET_THREADS_TIMEOUT("tabby.build.thread.timeout"),
    SET_BUILD_MODE("tabby.build.mode"),
    SET_EXCLUDE_LIBRARIES("tabby.build.libraries.excludes"),
    IS_FULL_CALL_GRAPH_CREATE("tabby.build.isFullCallGraphCreate"),
    IS_DOCKER_IMPORT_PATH("tabby.cache.isDockerImportPath"),
    IS_NEET_TO_CREATE_IGNORE_LIST("tabby.build.isNeedToCreateIgnoreList"),
    ;

    private String name="";
    ArgumentEnum(String name){
        this.name = name;
    }

    public String getValue(){
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
