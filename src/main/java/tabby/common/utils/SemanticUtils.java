package tabby.common.utils;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JimpleLocalBox;
import soot.tagkit.*;
import tabby.common.bean.ref.MethodReference;
import tabby.config.GlobalConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SemanticUtils {

    private static List<String> ARRAY_TYPES
            = Arrays.asList(
            // list
            "java.util.List",
            "java.util.Collection",
//                    "java.util.ArrayList",
//                    "java.util.LinkedList",
//                    "java.util.Stack",
//                    "java.util.Vector",
//                    "java.util.concurrent.CopyOnWriteArrayList",
            // set
            "java.util.Set",
//                    "java.util.HashSet",
//                    "java.util.LinkedHashSet",
//                    "java.util.SortedSet",
//                    "java.util.TreeSet",
//                    "java.util.concurrent.CopyOnWriteArraySet",
            // queue
            "java.util.Queue",
//                    "java.util.AbstractQueue",
//                    "java.util.ArrayDeque",
//                    "java.util.PriorityQueue",
            // map
            "java.util.Map"
//                    "java.util.HashMap",
//                    "java.util.HashTable",
//                    "java.util.Properties",
//                    "java.util.TreeMap",
//                    "java.util.LinkedHashMap",
//                    "java.util.WeakHashMap",
//                    "java.util.SortedMap",
//                    "java.util.concurrent.ConcurrentHashMap",
//                    "java.util.concurrent.ConcurrentMap"

    );
    private static Map<String, Set<String>> savedFatherNodes = Collections.synchronizedMap(new HashMap<>());
    private static Pattern PATTERN = Pattern.compile("This operation requires resolving level HIERARCHY but ([a-zA-Z0-9\\.\\$\\_\\']+) is at resolving level");

    /**
     * 提取value的name
     *
     * @param value
     * @return
     */
    public static String extractValueName(Object value) {
        String name = value.toString();
        if (value instanceof Local) {
            name = ((Local) value).getName();
        } else if (value instanceof InstanceFieldRef) {
            SootFieldRef ref = ((InstanceFieldRef) value).getFieldRef();
            if (ref != null) {
                name = ref.getSignature();
            }
        } else if (value instanceof SootField) {
            name = ((SootField) value).getSignature();
        } else if (value instanceof SootClass) {
            name = ((SootClass) value).getName();
        } else if (value instanceof ArrayRef) {
            Value base = ((ArrayRef) value).getBase();
            name = extractValueName(base);
        }

        return name;
    }

    public static String getRealCallType(InvokeExpr ie, MethodReference targetMethodRef) {
        String classname = "";
        List<ValueBox> valueBoxes = ie.getUseBoxes();
        for (ValueBox box : valueBoxes) {
            if (box instanceof JimpleLocalBox) {
                Value base = box.getValue();
                if (base != null) {
                    classname = base.getType().toString();
                }
                break;
            }
        }

        if (classname.isEmpty()) {
            classname = targetMethodRef.getClassname();
        }

        return classname;
    }

    public static String getInvokeType(InvokeExpr ie) {
        String invokeType = "";
        if (ie instanceof StaticInvokeExpr) {
            invokeType = "StaticInvoke";
        } else if (ie instanceof VirtualInvokeExpr) {
            invokeType = "VirtualInvoke";
        } else if (ie instanceof SpecialInvokeExpr) {
            invokeType = "SpecialInvoke";
        } else if (ie instanceof InterfaceInvokeExpr) {
            invokeType = "InterfaceInvoke";
        }
        return invokeType;
    }

    /**
     * return a,a[],a<f>b, a<a>
     *
     * @param value
     * @return
     */
    public static String getSimpleName0(Value value) {
        if (value instanceof InstanceFieldRef) {
            Value base = ((InstanceFieldRef) value).getBase();
            String baseName = getSimpleName0(base);
            SootFieldRef ref = ((InstanceFieldRef) value).getFieldRef();
            return baseName + "<f>" + ref.name();
        } else if (value instanceof Local) {
            return ((Local) value).getName();
        } else if (value instanceof ArrayRef) {
            Value base = ((ArrayRef) value).getBase();
            String name = getSimpleName0(base) + "<a>";
            return name.replace("<a><a>", "<a>"); // 当前处理方式 obj[][] 同 obj[] 的记录方式是一样的
        } else if (value instanceof CastExpr) {
            Value base = ((CastExpr) value).getOp();
            return getSimpleName0(base);
        } else if (value instanceof StaticFieldRef || value instanceof Constant) {
            return value.toString();
        }
//        else if(value instanceof Constant){
//            return null; // 不考虑常量
//        }
        else {
//            log.debug("getSimpleName0:"+value.toString());
            return null;
        }
    }

    public static boolean hasField(String cls, String fieldName) {
        if (fieldName.startsWith("<f>")) {
            fieldName = fieldName.substring(3);
        }
        fieldName = fieldName.replace("<a>", "");
        if (fieldName.contains("<f>")) {
            String[] pos = fieldName.split("<f>");
            boolean flag = true;
            for (String p : pos) {
                if (cls.endsWith("[]")) {
                    cls = cls.substring(0, cls.length() - 2);
                }
                SootField field = getField(cls, p);
                if (field == null) {
                    flag = false;
                    break;
                } else {
                    cls = field.getType().toString();
                }
            }
            return flag;
        } else {
            SootField field = getField(cls, fieldName);
            return field != null;
        }
    }

    public static boolean hasField(SootClass cls, String fieldName) {
        if (cls == null || fieldName == null) return false;
        SootField field = getField(cls, fieldName);
        return field != null;
    }

    public static boolean isExtendFrom(Set<String> sources, String dest) {
        if (sources.contains(dest) ||
                sources.contains("source")
                || sources.contains(PositionUtils.DAO_STRING)
                || sources.contains(PositionUtils.RPC_STRING)
                || sources.contains(PositionUtils.AUTH_STRING)
        ) return true;

        for (String source : sources) {
            SootClass cls = getSootClass(source);
            if (isExtendFrom(cls, dest)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isExtendFrom(SootClass source, String dest) {

        if (source != null) {
            if (dest.equals(source.getName())) return true;

            if (source.hasSuperclass()) {
                return isExtendFrom(source.getSuperclass(), dest);
            } else if (source.getInterfaceCount() > 0) {
                for (SootClass inface : source.getInterfaces()) {
                    if (isExtendFrom(inface, dest)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static InvokeExpr getInvokeExpr(Stmt stmt) throws InterruptedException {
        InvokeExpr ie = null;
        int i = 0;
        do {
            try {
                ie = stmt.getInvokeExpr();
            } catch (Exception e) {
                // try again
                int random = (int) (Math.random() * 100);
                Thread.sleep(random);
            }
            i++;
            // 防止因为多线程导致invoke重复提取
        } while (ie == null && i < 3);

        return ie;
    }

    public static SootMethod getMethod(InvokeExpr ie) {
        SootMethod method = null;
        int i = 0;
        do {
            try {
                method = ie.getMethod();
            } catch (Exception e) {
                // try again
                int random = (int) (Math.random() * 100);
                try {
                    Thread.sleep(random);
                } catch (InterruptedException ex) {
                    System.exit(0);
                }
            }
            i++;
            // 防止因为多线程导致invoke重复提取
        } while (method == null && i < 3);

        return method;
    }

    public static SootMethod getMethod(SootMethodRef sootMethodRef) {
        SootMethod method = null;
        int i = 0;
        do {
            try {
                method = sootMethodRef.resolve();
            } catch (Exception e) {
                // try again
                int random = (int) (Math.random() * 100);
                try {
                    Thread.sleep(random);
                } catch (InterruptedException ex) {
                    System.exit(0);
                }
            }
            i++;
            // 防止因为多线程导致invoke重复提取
        } while (method == null && i < 3);

        return method;
    }

    public static SootMethod getMethod(SootClass cls, String subSignature) {
        try {
            return cls.getMethod(subSignature);
        } catch (RuntimeException e) {
            SootMethod method = null;
            if (cls.hasSuperclass()) {
                method = getMethod(cls.getSuperclass(), subSignature);
            }

            if (method == null && cls.getInterfaceCount() > 0) {
                for (SootClass inface : cls.getInterfaces()) {
                    method = getMethod(inface, subSignature);
                    if (method != null) break;
                }
            }
            return method;
        }
    }

    public static SootMethod getMethod(String classname, String subSignature) {
        SootClass cls = getSootClass(classname);

        if (cls == null) return null;

        return getMethod(cls, subSignature);
    }

    public static synchronized Body retrieveBody(SootMethod method, String signature, boolean tries) {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Body body = null;
        try {
            // 为了解决soot获取body不停止的问题，添加线程且最多执行2分钟
            // 超过2分钟可以获取到的body，也可以间接认为是非常大的body，暂不分析
            // 这里两分钟改成配置文件timeout-1，最短1分钟
            Future<Body> future = executor.submit(() -> method.retrieveActiveBody());
            body = future.get(Integer.max(GlobalConfiguration.TIMEOUT - 1, 1) * 60L, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("Body retrieve error: method Fetch Timeout " + signature);
        } catch (ExecutionException | InterruptedException e) {
//            e.printStackTrace();
            String msg = e.getMessage();
            if (tries && msg != null && msg.contains("Failed to convert")) {
                Throwable temp = e;
                do {
                    String message = temp.getMessage();
                    if (message != null && message.contains("This operation requires resolving level HIERARCHY")) {
                        Matcher matcher = PATTERN.matcher(message);
                        if (matcher.find()) {
                            String classname = matcher.group(1);
                            if (classname != null && !classname.isEmpty()) {
                                Scene.v().addBasicClass(classname, SootClass.HIERARCHY);
                                Scene.v().loadClassAndSupport(classname);
                                body = retrieveBody(method, signature, false);
                            }
                        }
                        break;
                    }
                    temp = temp.getCause();
                } while (temp != null);
            }

            if (body == null) {
                throw new RuntimeException("Body retrieve error: " + e);
            }
        } finally {
            executor.shutdown();
        }

        return body;
    }

    public static SootField getField(String cls, String fieldName) {
        SootClass sc = getSootClass(cls);
        return getField(sc, fieldName);
    }

    public static String getFieldType(String classname, String field) {
        SootField f = getField(classname, field);
        if (f == null) return null;
        return f.getType().toString();
    }

    public static String getFieldType(SootClass cls, String[] fields) {
        SootField field = null;
        int len = fields.length;
        SootClass current = cls;
        String nextFieldType = null;
        for (int i = 1; i < len; i++) {
            if (current == null || current.isInterface() || "java.lang.Object".equals(current.getName())) {
                return "true";
            }
            String fieldName = fields[i];
            field = getField(current, fieldName);
            if (field == null) return null;
            nextFieldType = field.getType().toString();
            if (i + 1 < len) {
                current = getSootClass(nextFieldType);
            }
        }
        return nextFieldType;
    }

    public static SootField getField(InstanceFieldRef ref) throws InterruptedException {
        SootField field = ref.getField();
        if (field != null) return field;

        SootFieldRef fr = ref.getFieldRef();
        return getField(fr.declaringClass(), fr.name());
    }

    public static SootField getField(SootClass cls, String fieldName) {
        if (cls == null) return null;
        try {
            return cls.getFieldByName(fieldName);
        } catch (Exception ignore) {
            if (cls.hasSuperclass()) {
                return getField(cls.getSuperclass(), fieldName);
            }
        }
        return null;
    }

    public static SootClass getSootClass(String cls) {
        if (cls == null) return null;
        try {
            return Scene.v().getSootClass(cls);
        } catch (Exception e) {
            log.error("Load class {} error", cls);
        }
        return null;
    }

    public static String replaceFirst(String old, String identify, String replace) {
        if (old.contains(identify)) {
            int start = old.indexOf(identify);
            int end = start + identify.length();
            return old.substring(0, start) + replace + old.substring(end);
        }
        return null;
    }

    public static String getFieldNameByMethodName(String methodName) {
        String fieldName = null;
        if (methodName.startsWith("set") || methodName.startsWith("get")) {
            fieldName = methodName.substring(3); // getXxx setXxx
        } else if (methodName.startsWith("is")) {
            fieldName = methodName.substring(2); // isXxx
        }
        if (fieldName == null || fieldName.isEmpty()) return null;

        char firstChar = fieldName.charAt(0);
        if (Character.isLowerCase(firstChar)) return null; // check getS or gets

        firstChar = Character.toLowerCase(firstChar);
        String appendString = fieldName.substring(1);

        return firstChar + appendString;
    }

    public static boolean increaseAndCheck(String name, Map<String, Integer> map) {
        int pos = -1;
        if (map.containsKey(name)) {
            pos = map.get(name);
            if (pos > GlobalConfiguration.OBJECT_MAX_TRIGGER_TIMES) {
                // 强制结束赋值，一般情况下用不上这个，仅当存在特殊的while+swap之类的情况才可能触发
                return true;
            } else {
                map.put(name, pos + 1);
            }
        } else {
            map.put(name, 1);
        }
        return false;
    }

    public static Set<String> getAllFatherNodes(SootClass cls, boolean cacheFirst) {
        Set<String> nodes = new HashSet<>();
        if (cls == null) return nodes;
        String classname = cls.getName();
        if (cacheFirst && savedFatherNodes.containsKey(classname)) {
            return savedFatherNodes.get(classname);
        }

        if (cls.hasSuperclass() && !cls.getSuperclass().getName().equals("java.lang.Object")) {
            nodes.add(cls.getSuperclass().getName());
            nodes.addAll(getAllFatherNodes(cls.getSuperclass(), cacheFirst));
        }
        if (cls.getInterfaceCount() > 0) {
            cls.getInterfaces().forEach(intface -> {
                nodes.add(intface.getName());
                nodes.addAll(getAllFatherNodes(intface, cacheFirst));
            });
        }
        savedFatherNodes.put(classname, nodes);
        return nodes;
    }

    public static Set<String> getAllFatherNodes(String classname) {
        return savedFatherNodes.getOrDefault(classname, new HashSet<>());
    }

    public static boolean isSerializableClass(SootClass cls) {
        Set<String> relatedClassnames = SemanticUtils.getAllFatherNodes(cls, false);
        return relatedClassnames.contains("java.io.Serializable");
    }

    public static boolean isCollection(Type type) {
        String typeName = type.toString();
        Set<String> nodes = savedFatherNodes.get(typeName);
        if (nodes == null) {
            nodes = new HashSet<>();
        } else {
            nodes = new HashSet<>(nodes);
        }
        nodes.add(typeName);
        for (String node : nodes) {
            if (ARRAY_TYPES.contains(node)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> getAnnotationTypes(SootMethod method) {
        List<String> types = new ArrayList<>();
        List<Tag> tags = method.getTags();
        for (Tag tag : tags) {
            if (tag instanceof VisibilityAnnotationTag) {
                VisibilityAnnotationTag visibilityAnnotationTag = (VisibilityAnnotationTag) tag;
                for (AnnotationTag annotationTag : visibilityAnnotationTag.getAnnotations()) {
                    String type = annotationTag.getType();
                    type = type.replace("/", ".");
                    type = type.replace(";", "");
                    types.add(type);
                }
            }
        }
        return types;
    }

    public static String cleanTag(String str, String... tags) {
        String ret = str;
        for (String tag : tags) {
            ret = ret.replace(tag, "");
        }
        return ret;
    }

    public static Set<String> getHttpUrlPaths(Map<String, Map<String, Set<String>>> annotations) {
        Set<String> uris = new HashSet<>();
        for (Map.Entry<String, Map<String, Set<String>>> entry : annotations.entrySet()) {
            String annotationName = entry.getKey();
            if (annotationName == null || annotationName.isEmpty()) continue;

            if (annotationName.endsWith("Mapping") || annotationName.endsWith(".Path")) {
                Set<String> values = entry.getValue().get("value");
                if (values != null) {
                    uris.addAll(values);
                }
            }
        }
        return uris;
    }

    public static Set<String> getMRPCUrlPaths(Map<String, Map<String, Set<String>>> annotations) {
        Set<String> uris = new HashSet<>();
        for (Map.Entry<String, Map<String, Set<String>>> entry : annotations.entrySet()) {
            String annotationName = entry.getKey();
            if (annotationName == null || annotationName.isEmpty()) continue;

            if (annotationName.endsWith("OperationType")) {
                Set<String> values = entry.getValue().get("value");
                if (values != null) {
                    uris.addAll(values);
                }
            }
        }
        return uris;
    }

    public static String getHttpUrlPathWithBaseURLPaths(Map<String, Map<String, Set<String>>> annotations, Set<String> baseUrlPaths) {
        Set<String> urls = new HashSet<>();

        if (baseUrlPaths == null || baseUrlPaths.isEmpty()) {
            baseUrlPaths = new HashSet<>();
            baseUrlPaths.add("");
        }

        Set<String> subUrlPaths = getHttpUrlPaths(annotations);

        for (String base : baseUrlPaths) {
            for (String sub : subUrlPaths) {
                String urlPath = base;
                if (urlPath.isEmpty()) {
                    urlPath = sub;
                } else {
                    urlPath += "/" + sub;
                }
                if (urlPath.endsWith("/")) {
                    urlPath = urlPath.substring(0, urlPath.length() - 1);
                }

                urlPath = urlPath.replace("//", "/");
                if (!urlPath.isEmpty()) {
                    urls.add(urlPath);
                }
            }
        }

        if (urls.isEmpty()) {
            return "";
        } else {
            return String.join(",", urls);
        }
    }

    public static String calculateUuid(Object value) {
        String name = extractValueName(value);
        return HashingUtils.hashString(name);
    }

    public static boolean isInterface(Type type) {
        if (type instanceof RefType) {
            SootClass cls = getSootClass(((RefType) type).getClassName());
            return Modifier.isInterface(cls.getModifiers());
        }
        return false;
    }

    public static boolean isConstant(Type type) {
        if (type instanceof PrimType) {
            return true;
        } else if (type instanceof NullType) {
            return true;
        } else {
            return "java.lang.String".equals(type.toString());
        }
    }

    public static boolean isConstantArray(Type type) {
        if (type instanceof ArrayType) {
            return isConstant(((ArrayType) type).baseType);
        }
        return false;
    }

    public static boolean isNecessaryType(Value value) {
        Type type = null;
        if (value instanceof Local) {
            type = value.getType();
        } else if (value instanceof FieldRef) {
            SootFieldRef ref = ((FieldRef) value).getFieldRef();
            type = ref.type();
        } else if (value instanceof Constant) {
            type = value.getType();
        } else if (value instanceof ArrayRef) {
            Value base = ((ArrayRef) value).getBase();
            return isNecessaryType(base);
        }

        if (type != null) {
            if (type instanceof PrimType) {
                return type instanceof CharType || type instanceof ByteType;
            } else {
                return true;
            }
        }
        return true;
    }

    public static boolean isArray(Type type) {
        return type instanceof ArrayType;
    }


    public static boolean hasDefaultConstructor(SootClass cls) {
        if (cls == null) return false;

        try {
            SootMethod method = cls.getMethod("void <init>()");
            return method != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static Map<String, Map<String, Set<String>>> getAnnotations(List<Tag> tags) {
        Map<String, Map<String, Set<String>>> ret = new HashMap<>();
        for (Tag tag : tags) {
            if (tag instanceof VisibilityAnnotationTag) {
                VisibilityAnnotationTag visibilityAnnotationTag = (VisibilityAnnotationTag) tag;
                for (AnnotationTag annotationTag : visibilityAnnotationTag.getAnnotations()) {
                    String type = normalize(annotationTag.getType());
                    if ("kotlin.Metadata".equals(type)
                            || "shade.kotlin.Metadata".equals(type)
                            || type.startsWith("io.swagger.v3.oas.annotations.media.Schema")) {
                        continue;
                    }
                    Map<String, Set<String>> annotationTagInfo = new HashMap<>();
                    Collection<AnnotationElem> elems = annotationTag.getElems();
                    for (AnnotationElem elem : elems) {
                        String elemKey = elem.getName();
                        Set<String> elemValueList = new HashSet<>();
                        if (elem instanceof AnnotationArrayElem) {
                            ArrayList elemValues = ((AnnotationArrayElem) elem).getValues();
                            for (Object item : elemValues.stream().toArray()) {
                                if (item instanceof AnnotationStringElem) {
                                    AnnotationStringElem annotationStringElem = (AnnotationStringElem) item;
                                    elemValueList.add(annotationStringElem.getValue());
                                } else if (item instanceof AnnotationEnumElem) {
                                    AnnotationEnumElem annotationEnumElem = (AnnotationEnumElem) item;
                                    String enumName = String.format("%s.%s", normalize(annotationEnumElem.getTypeName()), annotationEnumElem.getName());
                                    elemValueList.add(enumName);
                                } else {
                                    elemValueList.add(item.toString());
                                }
                            }
                        }
                        if (elem instanceof AnnotationStringElem) {
                            AnnotationStringElem annotationStringElem = (AnnotationStringElem) elem;
                            elemValueList.add(annotationStringElem.getValue());
                        }
                        annotationTagInfo.put(elemKey, elemValueList);
                    }
                    ret.put(type, annotationTagInfo);
                }
            }
        }
        return ret;
    }

    private static String normalize(String type) {
        String ret = type.substring(1);
        ret = ret.substring(0, ret.length() - 1);
        return ret.replace("/", ".");
    }

    /**
     * 将两个action进行合并
     *
     * @param source
     * @param dest
     */
    public static void merge(Map<String, Set<String>> source, Map<String, Set<String>> dest) {
        Map<String, Set<String>> sourceCopy = ImmutableMap.copyOf(source);
        for (Map.Entry<String, Set<String>> entry : sourceCopy.entrySet()) {
            String key = entry.getKey();
            Set<String> actions = new CopyOnWriteArraySet<>(entry.getValue());
            actions.remove(key); // 防止出现 a : a 这种自己指向自己的情况

            if (actions.isEmpty()) continue;

            if (dest.containsKey(key)) {
                dest.get(key).addAll(actions);
            } else {
                dest.put(key, actions);
            }

//            Set<String> values = dest.get(key);
//            if (values != null && values.size() >= 2) {
//                // 当actions指向存在2个及以上时，尝试去除clear的状态
//                values.remove("clear");
//            }
        }
        // 如果同时出现 key 和 key<s> 这汇聚成key
        mergeS("return", dest);
    }

    private static void mergeS(String key, Map<String, Set<String>> dest) {
        if (dest == null) return;
        String keyS = key + "<s>";
        if (dest.containsKey(key) && dest.containsKey(keyS)) {
            if (dest.get(key) != null && dest.get(key).size() == 1 && dest.get(key).contains("clear")) {
                dest.remove(key);
                return;
            }
            Set<String> merge = dest.get(key);
            if (merge != null) {
                merge.addAll(dest.getOrDefault(keyS, new HashSet<>()));
                merge.removeIf("clear"::equals);
                dest.remove(keyS);
                if (merge.isEmpty()) {
                    dest.remove(key);
                }
            }
        }
    }

    public static void applySpecialMethodActions(MethodReference methodRef) {
        String action = methodRef.isDao() ? PositionUtils.DAO_STRING : PositionUtils.RPC_STRING;
        Set<String> actionSet = new HashSet<>();
        actionSet.add(action);
        Map<String, Set<String>> actions = new HashMap<>();
        actions.put("return<s>", actionSet);
        merge(actions, methodRef.getActions());
        methodRef.setInitialed(true);
        methodRef.setActionInitialed(true);
    }

    public static List<String> getTargetClasses(String filepath, Map<String, List<String>> moduleClasses) {
        List<String> classes = null;
        Path path = Paths.get(filepath);
        if (Files.notExists(path)) return null;

        if (JavaVersionUtils.isAtLeast(9) && moduleClasses != null) {
            String filename = path.getFileName().toString();
            if (filename.endsWith(".jmod")) {
                filename = filename.substring(0, filename.length() - 5);
            }
            classes = moduleClasses.get(filename);
        }

        if (classes == null) {
            classes = SourceLocator.v().getClassesUnder(filepath);
        }

        return classes;
    }
}
