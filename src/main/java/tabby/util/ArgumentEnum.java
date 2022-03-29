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
    SET_METHOD_MAX_DEPTH("tabby.build.method.maxDepth"),
    SET_METHOD_MAX_BODY_COUNT("tabby.build.method.maxBodyCount"),
    SET_INNER_DEBUG_ENABLE("tabby.debug.inner.details"),
    SET_THREADS_TIMEOUT("tabby.build.thread.timeout"),
    SET_INTER_PROCEDURAL("tabby.build.interProcedural"),
    SET_ALIAS_METHOD_COUNT("tabby.build.alias.maxCount"),
    SET_ARRAYS_MAX_LENGTH("tabby.build.array.maxLength"),
    SET_OBJECT_MAX_TRIGGER_TIMES("tabby.build.object.maxTriggerTimes"),
    IS_PRIM_TYPE_NEED_TO_CREATE("tabby.build.isPrimTypeNeedToCreate"),
    IS_FULL_CALL_GRAPH_CREATE("tabby.build.isFullCallGraphCreate")
    ;

    private String name="";
    ArgumentEnum(String name){
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
