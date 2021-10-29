package tabby.core.container;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.util.NumberedString;
import tabby.core.scanner.ClassInfoScanner;
import tabby.dal.caching.bean.edge.*;
import tabby.dal.caching.bean.ref.ClassReference;
import tabby.dal.caching.bean.ref.MethodReference;
import tabby.dal.caching.service.ClassRefService;
import tabby.dal.caching.service.MethodRefService;
import tabby.dal.caching.service.RelationshipsService;
import tabby.core.data.TabbyRule;
import tabby.dal.neo4j.service.ClassService;
import tabby.dal.neo4j.service.MethodService;

import java.util.*;

/**
 * global tabby.core.data container
 * @author wh1t3P1g
 * @since 2021/1/7
 */
@Getter
@Setter
@Slf4j
@Component
public class DataContainer {

    @Autowired
    private RulesContainer rulesContainer;

    @Autowired
    private ClassService classService;

    @Autowired
    private MethodService methodService;

    @Autowired
    private ClassRefService classRefService;
    @Autowired
    private MethodRefService methodRefService;
    @Autowired
    private RelationshipsService relationshipsService;

//    private Map<String, ClassReference> savedClassRefs = new HashMap<>();
    private Map<String, ClassReference> savedClassRefs = Collections.synchronizedMap(new HashMap<>());
//    private Map<String, MethodReference> savedMethodRefs = new HashMap<>();
    private Map<String, MethodReference> savedMethodRefs = Collections.synchronizedMap(new HashMap<>());

    private Set<Has> savedHasNodes = Collections.synchronizedSet(new HashSet<>());
    private Set<Call> savedCallNodes = Collections.synchronizedSet(new HashSet<>());
    private Set<Alias> savedAliasNodes = Collections.synchronizedSet(new HashSet<>());
    private Set<Extend> savedExtendNodes = Collections.synchronizedSet(new HashSet<>());
    private Set<Interfaces> savedInterfacesNodes = Collections.synchronizedSet(new HashSet<>());

    /**
     * check size and save nodes
     */
    public void save(String type){
        switch (type){
            case "class":
                if(!savedClassRefs.isEmpty()){
                    List<ClassReference> list = new ArrayList<>(savedClassRefs.values());
                    savedClassRefs.clear();
                    classRefService.save(list);
                }
                break;
            case "method":
                if(!savedMethodRefs.isEmpty()){
                    List<MethodReference> list = new ArrayList<>(savedMethodRefs.values());
                    savedMethodRefs.clear();
                    methodRefService.save(list);
                }
                break;
            case "has":
                if(!savedHasNodes.isEmpty()){
                    relationshipsService.saveAllHasEdges(savedHasNodes);
                    savedHasNodes.clear();
                }
                break;
            case "call":
                if(!savedCallNodes.isEmpty()){
                    relationshipsService.saveAllCallEdges(savedCallNodes);
                    savedCallNodes.clear();
                }
                break;
            case "extend":
                if(!savedExtendNodes.isEmpty()){
                    relationshipsService.saveAllExtendEdges(savedExtendNodes);
                    savedExtendNodes.clear();
                }
                break;
            case "interfaces":
                if(!savedInterfacesNodes.isEmpty()){
                    relationshipsService.saveAllInterfacesEdges(savedInterfacesNodes);
                    savedInterfacesNodes.clear();
                }
                break;
            case "alias":
                if(!savedAliasNodes.isEmpty()){
                    relationshipsService.saveAllAliasEdges(savedAliasNodes);
                    savedAliasNodes.clear();
                }
                break;
        }
    }

    /**
     * store nodes
     * insert if node not exist
     * replace if node exist
     * @param ref node
     * @param <T> node type
     */
    public <T> void store(T ref) {
        if(ref == null) return;

        if(ref instanceof ClassReference){
            ClassReference classRef = (ClassReference) ref;
            savedClassRefs.put(classRef.getName(), classRef);
        }else if(ref instanceof MethodReference){
            MethodReference methodRef = (MethodReference) ref;
            savedMethodRefs.put(methodRef.getSignature(), methodRef);
        }else if(ref instanceof Has){
            savedHasNodes.add((Has) ref);
        }else if(ref instanceof Call){
            savedCallNodes.add((Call) ref);
        }else if(ref instanceof Interfaces){
            savedInterfacesNodes.add((Interfaces) ref);
        }else if(ref instanceof Extend){
            savedExtendNodes.add((Extend) ref);
        }else if(ref instanceof Alias){
            savedAliasNodes.add((Alias) ref);
        }
    }

    public ClassReference getClassRefByName(String name){
        ClassReference ref = savedClassRefs.getOrDefault(name, null);
        if(ref != null) return ref;
        // find from h2
        ref = classRefService.getClassRefByName(name);
        return ref;
    }

    public MethodReference getMethodRefBySignature(String classname, String function, String signature){
        MethodReference ref = savedMethodRefs.getOrDefault(signature, null);
        if(ref != null) return ref;
        // find from h2
        ref = methodRefService.getMethodRefBySignature(signature);
        // new from rules
        if(ref == null){
            TabbyRule.Rule rule = rulesContainer.getRule(classname, function);
            if(rule != null && rule.getSignatures().contains(signature)){
                ref = MethodReference.newInstance(function, signature);
                ref.setSink(rule.isSink());
                ref.setPolluted(rule.isSink());
                ref.setIgnore(rule.isIgnore());
                ref.setSource(rule.isSource());
                ref.setActions(rule.getActions());
                ref.setPollutedPosition(rule.getPolluted());
                ref.setInitialed(true);
                ref.setActionInitialed(true);
                store(ref);
            }
        }
        return ref;
    }

    /**
     * 对于找不到的methodref
     * 1. 新建classRef 如果不存在的话
     * 2. 从classRef找methodRef
     * 3. 如果还是找不到则新建
     * @param sootMethodRef
     * @return
     */
    public MethodReference getOrAddMethodRef(SootMethodRef sootMethodRef, SootMethod method){
        SootClass cls = sootMethodRef.getDeclaringClass();
        MethodReference methodRef = getMethodRefBySignature(sootMethodRef);

        if(methodRef == null){
            // 解决ClassInfoScanner阶段，函数信息收集不完全的问题
            ClassReference classRef = getClassRefByName(cls.getName());
            if(classRef == null){// 对于新建class的情况，再查一遍
                classRef = ClassInfoScanner.collect0(cls.getName(), cls, this, 0);
                methodRef = getMethodRefBySignature(sootMethodRef);
            }

            if(methodRef == null){// 新建methodref
                methodRef = MethodReference.newInstance(classRef.getName(), method);
                Has has = Has.newInstance(classRef, methodRef);
                if(!classRef.getHasEdge().contains(has)){
                    classRef.getHasEdge().add(has);
                    store(has);
                    ClassInfoScanner.makeAliasRelation(has, this);
                }
                store(methodRef);
            }
        }
        return methodRef;
    }

    /**
     * 当前函数解决soot调用函数不准确的问题
     * soot的invoke表达式会将父类、接口等函数都归宿到当前类函数上，导致无法找到相应的methodRef
     * 解决这个问题，通过往父类、接口找相应的内容
     * 这里找到的是第一个找到的函数
     * @param sootMethodRef
     * @return
     */
    public MethodReference getMethodRefBySignature(SootMethodRef sootMethodRef){
        SootClass cls = sootMethodRef.getDeclaringClass();
        MethodReference target = findMethodRef(cls, sootMethodRef);
        if(target != null){
            return target;
        }

        return getMethodRefFromFatherNodes(sootMethodRef);
    }

    public MethodReference getMethodRefFromFatherNodes(SootMethodRef sootMethodRef){
        SootClass cls = sootMethodRef.getDeclaringClass();
        return findMethodRefFromFatherNodes(cls, sootMethodRef);
    }

    private MethodReference findMethodRefFromFatherNodes(SootClass cls, SootMethodRef sootMethodRef){
        // 父节点包括父类 和 接口
        MethodReference target = null;
        // 从父类找
        if(cls.hasSuperclass()){
            SootClass superCls = cls.getSuperclass();
            // 先往父类找 深度查找
            target = findMethodRefFromFatherNodes(superCls, sootMethodRef);
            // 如果父类没找到，则查找当前的类
            if(target == null){
                target = findMethodRef(superCls, sootMethodRef);
            }
            if(target != null){
                return target;
            }
        }
        // 从接口找
        if(cls.getInterfaceCount() > 0){
            for(SootClass intface:cls.getInterfaces()){
                // 先往父类找 深度查找
                target = findMethodRefFromFatherNodes(intface, sootMethodRef);
                // 如果父类没找到，则查找当前的类
                if(target == null){
                    target = findMethodRef(intface, sootMethodRef);
                }
                if(target != null){
                    return target;
                }
            }
        }
        return null;
    }

    private MethodReference findMethodRef(SootClass cls, SootMethodRef sootMethodRef){
        NumberedString subSignature = sootMethodRef.getSubSignature();
        try{
            SootMethod targetMethod = cls.getMethod(subSignature);
            return getMethodRefBySignature(cls.getName(), targetMethod.getName(), targetMethod.getSignature());
        }catch (RuntimeException e){
            // 仅处理override的情况 不处理overload的情况
//            // 通过函数名去找对应的函数
//            try{
//                SootMethod method = cls.getMethodByName(sootMethodRef.getName());
//                return getMethodRefBySignature(cls.getName(), method.getName(), method.getSignature());
//            }catch (RuntimeException ee){
//                // 找到了多个名字为methodName的函数
//                try{
//                    SootMethod target = sootMethodRef.resolve();
//                    for(SootMethod method:cls.getMethods()){// 对找到的第一个符合条件的函数进行返回
//                        if(sootMethodRef.getName().equals(method.getName()) && target.getParameterCount() == method.getParameterCount()){
//                            return getMethodRefBySignature(cls.getName(), method.getName(), method.getSignature());
//                        }
//                    }
//                }catch (Exception ignored){
//
//                }
//            }
        }
        return null;
    }

    public void loadNecessaryMethodRefs(){
        List<MethodReference> refs = methodRefService.loadNecessaryMethodRefs();
        refs.forEach(ref ->{
            savedMethodRefs.put(ref.getSignature(), ref);
        });
    }

    public void loadNecessaryClassRefs(){
        List<ClassReference> refs = classRefService.loadNecessaryClassRefs();
        refs.forEach(ref ->{
            savedClassRefs.put(ref.getName(), ref);
        });
    }

    public void save2Neo4j(){
        int nodes = classRefService.countAll() + methodRefService.countAll();
        int relations = relationshipsService.countAll();
        log.info("Total nodes: {}, relations: {}", nodes, relations);
        log.info("Clean old data in Neo4j.");
        classService.clear();
        log.info("Save methods to Neo4j.");
        methodService.importMethodRef();
        log.info("Save classes to Neo4j.");
        classService.importClassRef();
        log.info("Save relation to Neo4j.");
        classService.buildEdge();
    }

    public void save2CSV(){
        log.info("Save cache to CSV.");
        classRefService.save2Csv();
        methodRefService.save2Csv();
        relationshipsService.save2CSV();
        log.info("Save cache to CSV. DONE!");
    }

}
