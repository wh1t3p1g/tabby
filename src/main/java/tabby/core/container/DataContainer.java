package tabby.core.container;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import tabby.core.scanner.ClassInfoScanner;
import tabby.dal.caching.bean.edge.*;
import tabby.dal.caching.bean.ref.ClassReference;
import tabby.dal.caching.bean.ref.MethodReference;
import tabby.dal.caching.service.ClassRefService;
import tabby.dal.caching.service.MethodRefService;
import tabby.dal.caching.service.RelationshipsService;
import tabby.dal.neo4j.service.ClassService;
import tabby.dal.neo4j.service.MethodService;
import tabby.util.SemanticHelper;

import java.util.*;
import java.util.concurrent.Future;

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
     * 保存节点到h2 database
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
     * 保存节点到内存
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

    /**
     * 获取通过类名查找class节点
     * 优先在内存找，没有的话往数据库找
     * @param name
     * @return
     */
    public ClassReference getClassRefByName(String name){
        ClassReference ref = savedClassRefs.getOrDefault(name, null);
        if(ref != null) return ref;
        // find from h2
        ref = classRefService.getClassRefByName(name);
        return ref;
    }

    /**
     * 通过函数子签名和所属类 获取指定method节点
     * 优先在内存找，没有的话往数据库找
     * @param classname
     * @param subSignature
     * @return
     */
    public MethodReference getMethodRefBySubSignature(String classname, String subSignature){
        String signature = String.format("<%s: %s>", clean(classname), clean(subSignature));
        MethodReference ref = savedMethodRefs.getOrDefault(signature, null);
        if(ref != null) return ref;
        // find from h2
        ref = methodRefService.getMethodRefBySignature(signature);
        return ref;
    }

    private String clean(String data){
        return data.replace("'", "");
    }

    /**
     * 通过函数全签名找指定的method节点
     * 优先从内存找，没有的话往数据库找
     * 不递归从父节点找
     * @param signature
     * @return
     */
    public MethodReference getMethodRefBySignature(String signature){
        MethodReference ref = savedMethodRefs.getOrDefault(signature, null);
        if(ref != null) return ref;
        // find from h2
        ref = methodRefService.getMethodRefBySignature(signature);
        return ref;
    }

    /**
     * 当前函数解决soot调用函数不准确的问题
     * soot的invoke表达式会将父类、接口等函数都归宿到当前类函数上，导致无法找到相应的methodRef（这是因为父类未重载函数，内容将再父类上）
     * 解决这个问题，通过往父类、接口找相应的内容
     * 这里找到的是第一个找到的函数
     * @param sootMethodRef
     * @return
     */
    public MethodReference getMethodRefBySignature(SootMethodRef sootMethodRef){
        SootClass cls = sootMethodRef.getDeclaringClass();
        String subSignature = sootMethodRef.getSubSignature().toString();
        MethodReference target
                = getMethodRefBySubSignature(cls.getName(), subSignature);
        if(target != null){// 当前对象就能找到
            return target;
        }
        // 如果找不到，可能因为soot的问题，所以向父类继续查找
        return getFirstMethodRefFromFatherNodes(cls, subSignature, false);
    }

    /**
     * 跟tabby.core.container.DataContainer#getMethodRefBySignature(java.lang.String)一样
     * 但是会递归找父节点
     * @param classname
     * @param subSignature
     * @return
     */
    public MethodReference getMethodRefBySignature(String classname, String subSignature){
        try{ // getSootClass
            SootClass cls = SemanticHelper.loadClass(classname);
            try{
                SootMethod method = cls.getMethod(subSignature);
                if(method != null){
                    return getMethodRefBySignature(method.makeRef());
                }
            }catch (Exception e){
                // soot 不会有父类函数的继承，所以这里需要往父类去查找
                // 也意味着当前对象没有重载父类函数，所以会报错找不到
                return getFirstMethodRefFromFatherNodes(cls, subSignature, false);
            }
        }catch (Exception e){
            // 获取SootClass报错
            // 忽略
        }
        return null;
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
        // 递归查找父节点
        MethodReference methodRef = getMethodRefBySignature(sootMethodRef);

        if(methodRef == null){
            // 解决ClassInfoScanner阶段，函数信息收集不完全的问题
            SootClass cls = sootMethodRef.getDeclaringClass();
            ClassReference classRef = getClassRefByName(cls.getName());
            if(classRef == null){// 对于新建的情况，再查一遍
                classRef = ClassInfoScanner.collect0(cls.getName(), cls, this, 0);
                methodRef = getMethodRefBySignature(sootMethodRef);
            }

            if(methodRef == null){
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

    public MethodReference getFirstMethodRef(String classname, String subSignature){
        MethodReference target = getMethodRefBySubSignature(classname, subSignature);
        if(target != null) return target;

        SootClass sc = SemanticHelper.getSootClass(clean(classname));
        if(sc != null && sc.hasSuperclass()){
            target = getFirstMethodRef(sc.getSuperclass().getName(), subSignature);
        }
        return target;
    }

    /**
     * 根据Java语法，遇到第一个父类函数存在对应subSignature的函数是真实函数
     * 所以默认以广度优先进行查找，但也可指定深度优先
     * @param cls
     * @param subSignature
     * @return
     */
    public MethodReference getFirstMethodRefFromFatherNodes(SootClass cls, String subSignature, boolean deepFirst){
        // 父节点包括父类 和 接口
        MethodReference target = null;
        // 从父类找
        if(cls.hasSuperclass()){
            SootClass superCls = cls.getSuperclass();
            target = getTargetMethodRef(superCls, subSignature, deepFirst);

            if(target != null){
                return target;
            }
        }
        // 从接口找
        if(cls.getInterfaceCount() > 0){
            for(SootClass intface:cls.getInterfaces()){
                target = getTargetMethodRef(intface, subSignature, deepFirst);

                if(target != null){
                    return target;
                }
            }
        }
        return null;
    }

    public Set<MethodReference> getAliasMethodRefs(SootClass cls, String subSignature){
        Set<MethodReference> refs = new HashSet<>();
        Set<SootClass> classes = new HashSet<>();

        if(cls.hasSuperclass()){
            classes.add(cls.getSuperclass());
        }

        if(cls.getInterfaceCount() > 0){
            classes.addAll(cls.getInterfaces());
        }

        MethodReference ref = null;

        for(SootClass clazz:classes){
            ref = getMethodRefBySubSignature(clazz.getName(), subSignature);
            if(ref != null){
                refs.add(ref);
            }else{
                refs.addAll(getAliasMethodRefs(clazz, subSignature));
            }
        }
        return refs;
    }

    private MethodReference getTargetMethodRef(SootClass cls, String subSignature, boolean deepFirst){
        MethodReference target = null;
        if(deepFirst){
            target = getFirstMethodRefFromFatherNodes(cls, subSignature, deepFirst);
            if(target == null){
                target = getMethodRefBySubSignature(cls.getName(), subSignature);
            }
        }else{
            target = getMethodRefBySubSignature(cls.getName(), subSignature);
            if(target == null){
                target = getFirstMethodRefFromFatherNodes(cls, subSignature, deepFirst);
            }
        }

        return target;
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
        log.info("Clean old tabby.core.data in Neo4j.");
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

    @Async("tabby-collector")
    public Future<Boolean> cleanAll(){
        log.info("Clean old tabby.core.data in Neo4j.");
        classService.clear();
        log.info("Clean old tabby.core.data in Neo4j. DONE!");
        return new AsyncResult<>(true);
    }

    public void count(){
        int nodes = classRefService.countAll() + methodRefService.countAll();
        log.info("Total {}, classes: {}, methods: {}", nodes, classRefService.countAll(), methodRefService.countAll());
        relationshipsService.count();
    }

}