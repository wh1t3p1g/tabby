package tabby.core.collector;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import soot.SootClass;
import soot.SootMethod;
import soot.tagkit.AnnotationTag;
import soot.tagkit.Tag;
import soot.tagkit.VisibilityAnnotationTag;
import tabby.core.container.DataContainer;
import tabby.core.container.RulesContainer;
import tabby.core.data.TabbyRule;
import tabby.dal.caching.bean.edge.Has;
import tabby.dal.caching.bean.ref.ClassReference;
import tabby.dal.caching.bean.ref.MethodReference;
import tabby.util.SemanticHelper;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * @author wh1t3p1g
 * @since 2021/8/31
 */
@Service
public class ClassInfoCollector {

    @Autowired
    private DataContainer dataContainer;

    @Async("tabby-collector")
    public CompletableFuture<ClassReference> collect(SootClass cls){
        return CompletableFuture.completedFuture(collect0(cls, dataContainer));
    }

    /**
     * 仅收集classRef，不保存到内存
     * @param cls
     * @param dataContainer
     * @return
     */
    public static ClassReference collect0(SootClass cls, DataContainer dataContainer){
        ClassReference classRef = ClassReference.newInstance(cls);
        Set<String> relatedClassnames = getAllFatherNodes(cls);
        classRef.setSerializable(relatedClassnames.contains("java.io.Serializable"));
        classRef.setStrutsAction(relatedClassnames.contains("com.opensymphony.xwork2.ActionSupport")
                || relatedClassnames.contains("com.opensymphony.xwork2.Action"));
        // 提取类函数信息
        if(cls.getMethodCount() > 0){
            for (SootMethod method : cls.getMethods()) {
                extractMethodInfo(method, classRef, relatedClassnames, dataContainer);
            }
        }
        return classRef;
    }


    /**
     * 提取函数基础信息，并保存到内存中
     * @param method
     * @param ref
     */
    public static void extractMethodInfo(SootMethod method,
                                         ClassReference ref,
                                         Set<String> relatedClassnames,
                                         DataContainer dataContainer
    ){
        RulesContainer rulesContainer = dataContainer.getRulesContainer();
        String classname = ref.getName();
        MethodReference methodRef = MethodReference.newInstance(classname, method);
        TabbyRule.Rule rule = rulesContainer.getRule(classname, methodRef.getName());

        if (rule == null) { // 对于ignore类型，支持多级父类和接口的规则查找
            for (String relatedClassname : relatedClassnames) {
                TabbyRule.Rule tmpRule = rulesContainer.getRule(relatedClassname, methodRef.getName());
                if (tmpRule != null && tmpRule.isIgnore()) {
                    rule = tmpRule;
                    break;
                }
            }
        }
        boolean isSink = false;
        boolean isIgnore = false;
        boolean isSource = false;
        if(rule != null && (rule.isEmptySignaturesList() || rule.isContainsSignature(methodRef.getSignature()))){
            // 当rule存在signatures时，该rule为精确匹配，否则为模糊匹配，仅匹配函数名是否符合
            isSink = rule.isSink();
            isIgnore = rule.isIgnore();
            isSource = rule.isSource();

            // 此处，对于sink、know、ignore类型的规则，直接选取先验知识
            // 对于source类型 不赋予其actions和polluted
            if (!isSource) {
                Map<String, String> actions = rule.getActions();
                List<Integer> polluted = rule.getPolluted();
                if(isSink){
                    methodRef.setVul(rule.getVul());
                }
                methodRef.setActions(actions!=null?actions:new HashMap<>());
                methodRef.setPollutedPosition(polluted!=null?polluted:new ArrayList<>());
                methodRef.setActionInitialed(true);
                if(isIgnore){// 不构建ignore的类型
                    methodRef.setInitialed(true);
                }
            }
        }

        methodRef.setSink(isSink);
        methodRef.setIgnore(isIgnore);
        methodRef.setSource(isSource);
        methodRef.setEndpoint(ref.isStrutsAction() || isEndpoint(method, relatedClassnames));
        methodRef.setGetter(isGetter(method));
        methodRef.setSetter(isSetter(method));
        methodRef.setSerializable(relatedClassnames.contains("java.io.Serializable"));
        methodRef.setAbstract(method.isAbstract());
        methodRef.setHasDefaultConstructor(ref.isHasDefaultConstructor());

        Has has = Has.newInstance(ref, methodRef);
        ref.getHasEdge().add(has);
        dataContainer.store(has);
        dataContainer.store(methodRef);
    }


    /**
     * check method is an endpoint
     * @param method
     * @param relatedClassnames
     * @return
     */
    public static boolean isEndpoint(SootMethod method, Set<String> relatedClassnames){
        // check jsp _jspService
        if("_jspService".equals(method.getName())){
            return true;
        }

        // check from annotation
        List<Tag> tags = method.getTags();
        for (Tag tag : tags) {
            if (tag instanceof VisibilityAnnotationTag) {
                VisibilityAnnotationTag visibilityAnnotationTag = (VisibilityAnnotationTag) tag;
                for (AnnotationTag annotationTag : visibilityAnnotationTag.getAnnotations()) {
                    String type = annotationTag.getType();
                    if(type.endsWith("Mapping;")
                            || type.endsWith("javax/ws/rs/Path;")
                            || type.endsWith("javax/ws/rs/GET;")
                            || type.endsWith("javax/ws/rs/PUT;")
                            || type.endsWith("javax/ws/rs/DELETE;")
                            || type.endsWith("javax/ws/rs/POST;")){
                        return true;
                    }
                }
            }
        }

        // https://blog.csdn.net/melissa_heixiu/article/details/52472450
        List<String> requestTypes = new ArrayList<>(
                Arrays.asList("doGet","doPost","doPut","doDelete","doHead","doOptions","doTrace","service"));
        // check from servlet
        if((relatedClassnames.contains("javax.servlet.Servlet")
                || relatedClassnames.contains("javax.servlet.http.HttpServlet") // 防止依赖缺失情况下的识别
                || relatedClassnames.contains("javax.servlet.GenericServlet"))
                && requestTypes.contains(method.getName())){
            return true;
        }
        // not an endpoint
        return false;
    }

    public static boolean isGetter(SootMethod method){
        String methodName = method.getName();
        if(method.getParameterCount() == 0 && method.isPublic()){
            if(methodName.startsWith("get") || methodName.startsWith("is")){
                SootClass cls = method.getDeclaringClass();
                String fieldName = SemanticHelper.getFieldNameByMethodName(methodName);
                return SemanticHelper.hasField(cls, fieldName);
            }
        }

        return false;
    }

    public static boolean isSetter(SootMethod method){
        String methodName = method.getName();
        if(methodName.startsWith("set") && method.getParameterCount() == 1 && method.isPublic()){
            SootClass cls = method.getDeclaringClass();
            String fieldName = SemanticHelper.getFieldNameByMethodName(methodName);
            return SemanticHelper.hasField(cls, fieldName);
        }
        return false;
    }


    public static Set<String> getAllFatherNodes(SootClass cls){
        Set<String> nodes = new HashSet<>();
        if(cls.hasSuperclass() && !cls.getSuperclass().getName().equals("java.lang.Object")){
            nodes.add(cls.getSuperclass().getName());
            nodes.addAll(getAllFatherNodes(cls.getSuperclass()));
        }
        if(cls.getInterfaceCount() > 0){
            cls.getInterfaces().forEach(intface -> {
                nodes.add(intface.getName());
                nodes.addAll(getAllFatherNodes(intface));
            });
        }
        return nodes;
    }
}