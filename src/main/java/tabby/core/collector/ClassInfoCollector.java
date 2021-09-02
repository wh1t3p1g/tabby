package tabby.core.collector;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import soot.SootClass;
import soot.SootMethod;
import soot.tagkit.Tag;
import tabby.core.container.DataContainer;
import tabby.core.container.RulesContainer;
import tabby.core.data.TabbyRule;
import tabby.dal.caching.bean.edge.Has;
import tabby.dal.caching.bean.ref.ClassReference;
import tabby.dal.caching.bean.ref.MethodReference;

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

    @Async("collector")
    public CompletableFuture<ClassReference> collect(SootClass cls){
        return CompletableFuture.completedFuture(collect0(cls, dataContainer));
    }

    public static ClassReference collect0(SootClass cls, DataContainer dataContainer){
        ClassReference classRef = ClassReference.newInstance(cls);
        Set<String> relatedClassnames = getAllFatherNodes(cls);
        classRef.setSerializable(relatedClassnames.contains("java.io.Serializable"));

        // 提取类函数信息
        if(cls.getMethodCount() > 0){
            for (SootMethod method : cls.getMethods()) {
                extractMethodInfo(method, classRef, relatedClassnames, dataContainer);
            }
        }
        return classRef;
    }

    /**
     * 提取注解
     * @param tags
     * @return
     */
    public static List<String> getAnnotation(List<Tag> tags){
        List<String> annotations = new ArrayList<>();
        for(Tag tag:tags){
            annotations.add(tag.getName()+":"+new String(tag.getValue()));
        }
        return annotations;
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

    /**
     * 提取函数基础信息
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

        boolean isSink = rule != null && rule.isSink();
        boolean isIgnore = rule != null && rule.isIgnore();
        boolean isSource = rule != null && rule.isSource();

        methodRef.setSink(isSink);
        methodRef.setPolluted(isSink);
        methodRef.setIgnore(isIgnore);
        methodRef.setSource(isSource);
        methodRef.setSerializable(relatedClassnames.contains("java.io.Serializable"));
        // 此处，对于sink、know、ignore类型的规则，直接选取先验知识
        // 对于source类型 不赋予其actions和polluted
        if (rule != null && !isSource) {
            Map<String, String> actions = rule.getActions();
            List<Integer> polluted = rule.getPolluted();
            if(isSink){
                methodRef.setVul(rule.getVul());
            }
            methodRef.setActions(actions!=null?actions:new HashMap<>());
            methodRef.setPollutedPosition(polluted!=null?polluted:new ArrayList<>());
            methodRef.setInitialed(true);
            methodRef.setActionInitialed(true);
        }

        Has has = Has.newInstance(ref, methodRef);
        ref.getHasEdge().add(has);
        dataContainer.store(has);
        dataContainer.store(methodRef);
    }
}
