package tabby.core.scanner;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.*;
import tabby.config.GlobalConfiguration;
import tabby.core.data.DataContainer;
import tabby.core.data.RulesContainer;
import tabby.core.data.TabbyRule;
import tabby.caching.bean.edge.Alias;
import tabby.caching.bean.edge.Extend;
import tabby.caching.bean.edge.Has;
import tabby.caching.bean.edge.Interfaces;
import tabby.caching.bean.ref.ClassReference;
import tabby.caching.bean.ref.MethodReference;

import java.util.*;

/**
 * 处理jdk相关类的信息抽取
 * @author wh1t3P1g
 * @since 2020/10/21
 */
@Data
@Slf4j
@Component
public class ClassInfoScanner {

    @Autowired
    private DataContainer dataContainer;
    @Autowired
    private RulesContainer rulesContainer;

    public void run(List<String> classes){
        collect(classes);
        save();
    }

    public void collect(List<String> classes){
        if(classes.isEmpty()) return;
        log.info("Load necessary class refs for tabby.");
        dataContainer.loadNecessaryClassRefs();
        log.info("Start to collect classes information.");
        classes.forEach(classname ->{
            ClassInfoScanner.collect(classname, dataContainer, rulesContainer, false);
        });
        int clsSize = dataContainer.getSavedClassRefs().size();
        dataContainer.getSavedClassRefs().forEach((name, ref) -> {
            makeAliasRelation(ref, dataContainer);
        });
        log.info("Collect <"+clsSize+"> classes information.");
    }

    /**
     * 根据单个类进行类信息收集
     * @param classname 待收集的类名
     * @return 具体的类信息
     */
    public static ClassReference collect(String classname, DataContainer dataContainer, RulesContainer rulesContainer, boolean force){
        ClassReference classRef = null;
        try{
            SootClass cls = Scene.v().getSootClass(classname);

            if(!cls.isPhantom() || force) {
                classRef = ClassReference.newInstance(cls.getName());
                classRef.setInterface(cls.isInterface());
                Set<String> relatedClassnames = getAllFatherNodes(cls);
                classRef.setSerializable(relatedClassnames.contains("java.io.Serializable"));
                // 提取类属性信息
                if(cls.getFieldCount() > 0){
                    for (SootField field : cls.getFields()) {
                        List<String> fieldInfo = new ArrayList<>();
                        fieldInfo.add(field.getName());
                        fieldInfo.add(field.getModifiers() + "");
                        fieldInfo.add(field.getType().toString());
                        classRef.getFields().add(GlobalConfiguration.GSON.toJson(fieldInfo));
                    }
                }
                // 提取父类信息
                if(cls.hasSuperclass() && !cls.getSuperclass().getName().equals("java.lang.Object")){
                    // 剔除Object类的继承关系，节省继承边数量
                    classRef.setHasSuperClass(cls.hasSuperclass());
                    classRef.setSuperClass(cls.getSuperclass().getName());
                    extractSuperClassInfo(cls.getSuperclass().getName(), classRef, dataContainer, rulesContainer);
                }
                // 提取接口信息
                if(cls.getInterfaceCount() > 0){
                    classRef.setHasInterfaces(true);
                    for (SootClass intface : cls.getInterfaces()) {
                        classRef.getInterfaces().add(intface.getName());
                        extractInterfaceInfo(intface.getName(), classRef, dataContainer, rulesContainer);
                    }
                }

                // 提取类函数信息
                if(cls.getMethodCount() > 0){
                    for (SootMethod method : cls.getMethods()) {
                        extractMethodInfo(method, classRef, relatedClassnames, dataContainer, rulesContainer);
                    }
                }

                dataContainer.store(classRef);
            }
        }catch (Exception e){
            // class not found
        }
        return classRef;
    }

    public static void makeAliasRelation(ClassReference ref, DataContainer dataContainer){
        if(ref == null)return;
        // build alias relationship
        if(ref.getHasEdge() == null) return;

        ref.getHasEdge().forEach(has -> {
            MethodReference sourceRef = has.getMethodRef();
            SootMethod sootMethod = sourceRef.getMethod();
            if(sootMethod == null)return;
            SootMethodRef sootMethodRef = sootMethod.makeRef();
            MethodReference targetRef = dataContainer.getMethodRefFromFatherNodes(sootMethodRef);
            if(targetRef != null
                    && !targetRef.getSignature().equals("<java.lang.Object: void <init>()>")
                    && targetRef.getParameters().size() == sourceRef.getParameters().size()
            ){ // 别名关系 参数类型可以不一样 但 参数数量一定要一样
                Alias alias = Alias.newInstance(sourceRef, targetRef);
                sourceRef.setAliasEdge(alias);
                dataContainer.store(alias);
            }
        });
        ref.setInitialed(true);
    }



    public static void extractSuperClassInfo(String superclass, ClassReference ref, DataContainer dataContainer, RulesContainer rulesContainer){
        ClassReference superclassRef = dataContainer.getClassRefByName(superclass);
        if(superclassRef == null){
            superclassRef = collect(superclass, dataContainer, rulesContainer, false);
        }
        if(superclassRef != null){
            Extend extend =  Extend.newInstance(ref, superclassRef);
            ref.setExtendEdge(extend);
            dataContainer.store(extend);
        }
    }

    public static void extractMethodInfo(SootMethod method, ClassReference ref,
                                         Set<String> relatedClassnames,
                                         DataContainer dataContainer, RulesContainer rulesContainer){
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

    public static void extractInterfaceInfo(String intface, ClassReference ref, DataContainer dataContainer, RulesContainer rulesContainer){
        ClassReference interfaceRef = dataContainer.getClassRefByName(intface);
        if(interfaceRef == null){
            interfaceRef = collect(intface, dataContainer, rulesContainer, false);
        }
        if(interfaceRef != null){
            Interfaces interfaces = Interfaces.newInstance(ref, interfaceRef);
            ref.getInterfaceEdge().add(interfaces);
            dataContainer.store(interfaces);
        }
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


    public void save(){
        log.info("Start to save remained data to graphdb.");
        dataContainer.save("class");
        dataContainer.save("has");
        dataContainer.save("alias");
        dataContainer.save("extend");
        dataContainer.save("interfaces");
        log.info("Graphdb saved.");
    }


}