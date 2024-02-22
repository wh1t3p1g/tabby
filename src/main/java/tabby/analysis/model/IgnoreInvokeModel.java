package tabby.analysis.model;

import soot.jimple.Stmt;
import tabby.common.bean.ref.MethodReference;
import tabby.config.GlobalConfiguration;
import tabby.core.container.DataContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author wh1t3p1g
 * @since 2021/9/1
 */
public class IgnoreInvokeModel extends DefaultInvokeModel {

    private static final List<String> IGNORE_LIST = new ArrayList<>(Arrays.asList(
            "<java.lang.Object: void <init>()>",
            "<java.io.PrintStream: void println(java.lang.String)>",
            "<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>",
            "<java.lang.StringBuilder: java.lang.AbstractStringBuilder append(float)>",
            "<java.lang.StringBuilder: java.lang.String toString()>",
            "<java.lang.StringBuilder: int length()>",
            "<java.lang.String: int hashCode()>",
            "<java.lang.String: int length()>"
//            "<java.lang.String: boolean equals(java.lang.Object)>"
    ));

    private static final List<String> WEB_MODE_IGNORE_LIST = new ArrayList<>(Arrays.asList(
            "java.lang.String toString()",
            "int hashCode()",
            "void close()",
            "void flush()",
            "int length()",
            "java.lang.Class getClass()"
//            "boolean equals(java.lang.Object)"
    ));

    private static final List<String> EXCLUDE_LIST = new ArrayList<>(Arrays.asList(
            "contains",
            "equals"
    ));

    private static final List<String> WEB_MODE_IGNORE_CLASSNAME_LIST = new ArrayList<>(Arrays.asList(
            "java.util.List",
            "java.util.ArrayList",
            "java.util.CopyOnWriteArrayList",
            "java.util.LinkedList",
            "java.util.Stack",
            "java.util.Vector",
            "java.util.Set",
            "java.util.LinkedHashSet",
            "java.util.TreeSet",
            "java.util.HashSet",
            "java.util.Collection",
            "java.util.Queue",
            "java.util.AbstractQueue",
            "java.util.PriorityQueue",
            "java.util.ArrayDeque",
            "java.util.HashTable",
            "java.util.concurrent.CopyOnWriteArraySet",
            "java.util.Map",
            "java.util.HashMap",
            "java.util.TreeMap",
            "java.util.LinkedHashMap",
            "java.util.WeakHashMap",
            "java.util.SortedMap",
            "java.util.concurrent.ConcurrentHashMap",
            "java.util.concurrent.ConcurrentMap",
            "java.util.Arrays",
            "java.util.Properties",
            "java.util.Iterator",
            "java.util.Enumeration",
            "java.io.PrintStream",
            "java.lang.String",
            "java.lang.StringBuilder"
    ));

    @Override
    public boolean apply(Stmt stmt, boolean isManual, MethodReference caller, MethodReference callee, DataContainer dataContainer) {
        String signature = callee.getSignature();
        if(IGNORE_LIST.contains(signature)){
            return true;
        }

        if(GlobalConfiguration.IS_WEB_MODE){
            String name = callee.getName();
            if(EXCLUDE_LIST.contains(name)){
                return false;
            }

            String subSignature = callee.getSubSignature();
            if(WEB_MODE_IGNORE_LIST.contains(subSignature)){
                return true;
            }

            String classname = callee.getClassname();
            if(WEB_MODE_IGNORE_CLASSNAME_LIST.contains(classname)){
                return true;
            }
        }

        return false;
    }
}
