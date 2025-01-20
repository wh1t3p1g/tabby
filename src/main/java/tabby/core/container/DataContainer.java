package tabby.core.container;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.jimple.InvokeExpr;
import tabby.analysis.data.Context;
import tabby.common.bean.edge.*;
import tabby.common.bean.ref.ClassReference;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.SemanticUtils;
import tabby.config.GlobalConfiguration;
import tabby.core.collector.ClassInfoCollector;
import tabby.dal.service.ClassRefService;
import tabby.dal.service.MethodRefService;
import tabby.dal.service.RelationshipsService;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * global tabby.core.data container
 *
 * @author wh1t3P1g
 * @since 2021/1/7
 */
@Getter
@Setter
@Slf4j
@Component
public class DataContainer {

    @Autowired
    private ClassRefService classRefService;
    @Autowired
    private MethodRefService methodRefService;
    @Autowired
    private RelationshipsService relationshipsService;

    private Map<String, ClassReference> savedClassRefs = Collections.synchronizedMap(new HashMap<>());
    private Map<String, MethodReference> savedMethodRefs = Collections.synchronizedMap(new HashMap<>());
    private Set<Call> savedCallEdges = Collections.synchronizedSet(new HashSet<>());
    private Set<String> newAddedMethodSigs = Collections.synchronizedSet(new HashSet<>());
    private Set<String> analyseTimeoutMethodSigs = Collections.synchronizedSet(new HashSet<>());
    private Set<String> onDemandMethods = Collections.synchronizedSet(new HashSet<>());
    private Map<String, Context> runningMethods = Collections.synchronizedMap(new HashMap<>());

    private Set<String> targets = Collections.synchronizedSet(new HashSet<>());

    public void save(String type) {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        switch (type) {
            case "class":
                if (!savedClassRefs.isEmpty()) {
                    List<ClassReference> copied = ImmutableList.copyOf(savedClassRefs.values());
                    futures.add(classRefService.save(copied));
                    savedClassRefs.clear();
                }
                break;
            case "method":
                if (!savedMethodRefs.isEmpty()) {
                    List<MethodReference> copied = ImmutableList.copyOf(savedMethodRefs.values());
                    futures.add(methodRefService.save(copied));
                    savedMethodRefs.clear();
                }
                break;
            case "call":
                if (!savedCallEdges.isEmpty()) {
                    Set<Call> copied = ImmutableSet.copyOf(savedCallEdges);
                    futures.add(saveCallEdge(copied));
                    savedCallEdges.clear();
                }
        }

        // wait for all futures
        for (CompletableFuture<Boolean> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
//                e.printStackTrace();
                log.error(e.getMessage());
            }
        }
    }

    public Context getRunningMethodContext(String signature) {
        return runningMethods.get(signature);
    }

    @Async("tabby-saver")
    public <T> void save(T data) {
        if (data instanceof ClassReference) {
            classRefService.save((ClassReference) data);
        } else if (data instanceof MethodReference) {
            methodRefService.save((MethodReference) data);
        } else if (data instanceof Edge) {
            if (data instanceof Has) {
                Has has = (Has) data;
                relationshipsService.saveHasEdge(has.getId(), has.getClassId(), has.getMethodRef().getId());
            } else if (data instanceof Call) {
                relationshipsService.saveCallEdge((Call) data);
            } else if (data instanceof Extend) {
                relationshipsService.saveExtendEdge((Extend) data);
            } else if (data instanceof Interfaces) {
                relationshipsService.saveInterfaceEdge((Interfaces) data);
            } else if (data instanceof Alias) {
                relationshipsService.saveAliasEdge((Alias) data);
            }
        }
    }

    @Async("tabby-saver")
    public CompletableFuture<Boolean> saveCallEdge(Set<Call> data) {
        if (data != null && !data.isEmpty()) {
            relationshipsService.saveAllCallEdges(data);
        }
        return CompletableFuture.completedFuture(true);
    }

    /**
     * store nodes
     * 保存节点到内存
     * insert if node not exist
     * replace if node exist
     *
     * @param ref node
     * @param <T> node type
     */
    public <T> void store(T ref, boolean saveToDB) {
        if (ref == null) return;

        if (ref instanceof ClassReference) {
            ClassReference classRef = (ClassReference) ref;
            savedClassRefs.put(classRef.getName(), classRef);
        } else if (ref instanceof MethodReference) {
            MethodReference methodRef = (MethodReference) ref;
            savedMethodRefs.put(methodRef.getSignature(), methodRef);
            targets.add(methodRef.getSignature());
        } else if (ref instanceof Set) {
            // 目前只有call会进来
            savedCallEdges.addAll((Set<Call>) ref);
        }

        if (saveToDB) {
            save(ref);
        }
    }

    /**
     * 获取通过类名查找class节点
     * 优先在内存找，没有的话往数据库找
     *
     * @param name
     * @return
     */
    public ClassReference getClassRefByName(String name, boolean isNeedFetchFromDatabase) {
        ClassReference ref = savedClassRefs.getOrDefault(clean(name), null);
        if (ref != null) return ref;
        // find from h2
        if (isNeedFetchFromDatabase) {
            ref = classRefService.getClassRefByName(clean(name));
            if (ref != null) {
                store(ref, false);
            }
            return ref;
        }
        return null;
    }

    /**
     * 通过函数子签名和所属类 获取指定method节点
     * 优先在内存找，没有的话往数据库找
     *
     * @param classname
     * @param subSignature
     * @return
     */
    public MethodReference getMethodRefBySubSignature(String classname, String subSignature, boolean useCache) {
        String signature = String.format("<%s: %s>", clean(classname), clean(subSignature));
        MethodReference ref = savedMethodRefs.getOrDefault(signature, null);
        if (ref != null || !useCache) return ref;
        // find from h2
        ref = methodRefService.getMethodRefBySignature(signature);
        if (ref != null) {
            store(ref, false);
        }
        return ref;
    }

    public MethodReference getOrAddMethodRefBySubSignature(String classname, String subSignature) {
        MethodReference ref = getMethodRefBySubSignature(classname, subSignature, true);
        if (ref == null) {
            SootMethod method = SemanticUtils.getMethod(classname, subSignature);
            if (method != null) {
                ref = getOrAddMethodRef(method);
            }
        }
        return ref;
    }

    private String clean(String data) {
        return data.replace("'", "");
    }

    /**
     * 通过函数全签名找指定的method节点
     * 优先从内存找，没有的话往数据库找
     * 不递归从父节点找
     *
     * @param signature
     * @return
     */
    public MethodReference getMethodRefBySignature(String signature, boolean isNeedFromDatabase) {
        MethodReference ref = savedMethodRefs.getOrDefault(clean(signature), null);
        if (ref != null) return ref;
        if (isNeedFromDatabase) {
            // find from h2
            ref = methodRefService.getMethodRefBySignature(clean(signature));
            if (ref != null) {
                store(ref, false);
            }
        }
        return ref;
    }

    /**
     * 当前函数解决soot调用函数不准确的问题
     * soot的invoke表达式会将父类、接口等函数都归宿到当前类函数上，导致无法找到相应的methodRef（这是因为父类未重载函数，内容将再父类上）
     * 解决这个问题，通过往父类、接口找相应的内容
     * 这里找到的是第一个找到的函数
     *
     * @param sootMethodRef
     * @return
     */
    public MethodReference getMethodRefBySignature(SootMethodRef sootMethodRef) {
        SootClass cls = sootMethodRef.getDeclaringClass();
        String subSignature = sootMethodRef.getSubSignature().toString();
        MethodReference target
                = getMethodRefBySubSignature(cls.getName(), subSignature, true);
        if (target != null) {// 当前对象就能找到
            return target;
        }
        try {
            SootMethod method = cls.getMethod(subSignature);
            if (method != null) {
                // 当前类是存在该函数的 只是前期没处理到这个函数，可以交给addMethodRef去处理
                return null;
            }
        } catch (Exception ignore) {
            // 如果没有找到method 会报错 但是不影响
        }

        // 如果找不到，可能因为soot的问题，所以向父类继续查找
        return getFirstMethodRefFromFatherNodes(cls, subSignature, false);
    }

    /**
     * 跟tabby.core.container.DataContainer#getMethodRefBySignature(java.lang.String)一样
     * 但是会递归找父节点
     *
     * @param classname
     * @param subSignature
     * @return
     */
    public MethodReference getMethodRefBySignature(String classname, String subSignature) {
        try {
            SootClass cls = SemanticUtils.getSootClass(classname);
            if (cls == null) return null;
            try {
                SootMethod method = cls.getMethod(subSignature);
                if (method != null) {
                    return getMethodRefBySignature(method.makeRef());
                }
            } catch (Exception e) {
                // soot 不会有父类函数的继承，所以这里需要往父类去查找
                // 也意味着当前对象没有重载父类函数，所以会报错找不到
                return getFirstMethodRefFromFatherNodes(cls, subSignature, false);
            }
        } catch (Exception e) {
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
     *
     * @param sootMethodRef
     * @return
     */
    public MethodReference getOrAddMethodRef(SootMethodRef sootMethodRef) {
        // 递归查找父节点
        MethodReference methodRef = getMethodRefBySignature(sootMethodRef);

        if (methodRef == null) {
            methodRef = addMethodRef(sootMethodRef);
        }
        return methodRef;
    }

    public MethodReference getOrAddMethodRef(SootMethod method) {
        // 递归查找父节点
        String signature = method.getSignature();
        MethodReference methodRef = getMethodRefBySignature(signature, true);

        if (methodRef == null) {
            SootClass cls = method.getDeclaringClass();
            methodRef = addMethodRef(cls, method);
        }
        return methodRef;
    }

    public MethodReference getOrAddMethodRef(InvokeExpr ie) {
        SootMethod sootMethod = SemanticUtils.getMethod(ie);
        if (sootMethod != null) {
            return getOrAddMethodRef(sootMethod);
        }

        SootMethodRef sootMethodRef = ie.getMethodRef();

        MethodReference methodRef = getMethodRefBySignature(sootMethodRef);

        if (methodRef == null) {
            methodRef = addMethodRef(sootMethodRef);
        }
        return methodRef;
    }

    public MethodReference addMethodRef(SootMethodRef sootMethodRef) {
        // 解决ClassInfoScanner阶段，函数信息收集不完全的问题
        SootClass cls = sootMethodRef.getDeclaringClass();
        SootMethod method = SemanticUtils.getMethod(sootMethodRef);
        return addMethodRef(cls, method);
    }

    public MethodReference addMethodRef(SootClass cls, SootMethod method) {
        // 解决ClassInfoScanner阶段，函数信息收集不完全的问题
        MethodReference methodRef = null;
        ClassReference classRef = getClassRefByName(cls.getName(), true);
        if (classRef == null) {// 对于新建的情况，再查一遍
            ClassInfoCollector.collectRuntimeForSingleClazz(cls.getName(), true, this, null);
            methodRef = getMethodRefBySignature(method.getSignature(), true);
        } else if (method != null &&
                ("soot.dummy.InvokeDynamic".equals(cls.getName())
//                        || "java.lang.invoke.VarHandle".equals(cls.getName())
//                        || "java.lang.invoke.MethodHandle".equals(cls.getName())
                        || cls.getName().contains("$lambda_")
                        || method.isNative()
                )) {
            // force add Dynamic method
            methodRef = ClassInfoCollector.collectSingleMethodRef(classRef, method, false, false, this, GlobalConfiguration.rulesContainer);
        } else if (method != null) {
            methodRef = ClassInfoCollector.collectSingleMethodRef(classRef, method, true, true, this, GlobalConfiguration.rulesContainer);
        }

        return methodRef;
    }

    public MethodReference getFirstMethodRef(String classname, String subSignature) {
        MethodReference target = getMethodRefBySubSignature(classname, subSignature, true);
        if (target != null) return target;

        SootClass sc = SemanticUtils.getSootClass(clean(classname));
        if (sc != null && sc.hasSuperclass()) {
            target = getFirstMethodRef(sc.getSuperclass().getName(), subSignature);
        }
        return target;
    }

    public MethodReference getFirstMethodRefFromChild(String classname, String subSignature) {
        MethodReference target = getFirstMethodRef(classname, subSignature);
        if (target != null && !target.isAbstract()) return target;
        // 抽象类时 找子类实现
//        ClassReference classRef = getClassRefByName(classname);
//        for(String child:classRef.getChildClassnames()){
//            target = getFirstMethodRef(child, subSignature);
//            if(target != null && !target.isAbstract()) return target;
//        }
        return null;
    }

    public String getClassnameByField(String classname, String field, Set<String> visited) {
        if (visited.contains(classname)) return null;
        visited.add(classname);

        ClassReference classRef = getClassRefByName(classname, true);
        if (classRef == null) return SemanticUtils.getFieldType(classname, field);

        if (classRef.getFields().containsKey(field)) {
            return classRef.getFields().get(field);
        } else {
            String cls = null;
            // 往父类找
            if (classRef.isHasSuperClass()) {
                cls = getClassnameByField(classRef.getSuperClass(), field, visited);
            }
            if (cls != null) return cls;
            // 往子类找
            List<String> kids = new ArrayList<>(classRef.getChildClassnames());
            for (String kid : kids) {
                cls = getClassnameByField(kid, field, visited);
                if (cls != null) return cls;
            }
            // 都没找到
            return null;
        }

    }

    /**
     * 根据Java语法，遇到第一个父类函数存在对应subSignature的函数是真实函数
     * 所以默认以广度优先进行查找，但也可指定深度优先
     *
     * @param cls
     * @param subSignature
     * @return
     */
    public MethodReference getFirstMethodRefFromFatherNodes(SootClass cls, String subSignature, boolean deepFirst) {
        // 父节点包括父类 和 接口
        MethodReference target = null;
        // 从父类找
        if (cls.hasSuperclass()) {
            SootClass superCls = cls.getSuperclass();
            target = getTargetMethodRef(superCls, subSignature, deepFirst);

            if (target != null) {
                return target;
            }
        }
        // 从接口找
        if (cls.getInterfaceCount() > 0) {
            for (SootClass intface : cls.getInterfaces()) {
                target = getTargetMethodRef(intface, subSignature, deepFirst);

                if (target != null) {
                    return target;
                }
            }
        }
        return null;
    }

    public Set<MethodReference> getAliasMethodRefs(SootClass cls, String subSignature) {
        Set<MethodReference> refs = new HashSet<>();
        Set<SootClass> classes = new HashSet<>();

        if (cls.hasSuperclass()) {
            classes.add(cls.getSuperclass());
        }

        if (cls.getInterfaceCount() > 0) {
            classes.addAll(cls.getInterfaces());
        }

        for (SootClass clazz : classes) {
            MethodReference ref = getMethodRefBySubSignature(clazz.getName(), subSignature,
                    GlobalConfiguration.IS_BUILD_WIH_CACHE_ENABLE);
            if (ref != null) {
                refs.add(ref);
            } else {
                refs.addAll(getAliasMethodRefs(clazz, subSignature));
            }
        }
        return refs;
    }

    private MethodReference getTargetMethodRef(SootClass cls, String subSignature, boolean deepFirst) {
        MethodReference target = null;
        if (deepFirst) {
            target = getFirstMethodRefFromFatherNodes(cls, subSignature, deepFirst);
            if (target == null) {
                target = getMethodRefBySubSignature(cls.getName(), subSignature, true);
            }
        } else {
            target = getMethodRefBySubSignature(cls.getName(), subSignature, true);
            if (target == null) {
                target = getFirstMethodRefFromFatherNodes(cls, subSignature, deepFirst);
            }
        }

        return target;
    }

    public void count() {
        int classNodes = classRefService.countAll();
        int methodNodes = methodRefService.countAll();
        int nodes = classNodes + methodNodes;
        log.info("Total {}, classes: {}, methods: {}", nodes, classNodes, methodNodes);
        relationshipsService.count();
    }

    public void keepDbAlive() {
        classRefService.countAll();
    }

    public void save2CSV() {
        log.info("transfer db to csv");
        classRefService.save2Csv(GlobalConfiguration.CLASSES_OUTPUT_PATH);
        methodRefService.save2Csv(GlobalConfiguration.METHODS_OUTPUT_PATH);
        relationshipsService.saveAliasToCsv(GlobalConfiguration.ALIAS_RELATIONSHIP_OUTPUT_PATH);
        relationshipsService.saveHasToCsv(GlobalConfiguration.HAS_RELATIONSHIP_OUTPUT_PATH);
        relationshipsService.saveExtendToCsv(GlobalConfiguration.EXTEND_RELATIONSHIP_OUTPUT_PATH);
        relationshipsService.saveCallToCsv(GlobalConfiguration.CALL_RELATIONSHIP_OUTPUT_PATH);
        relationshipsService.saveInterfaceToCsv(GlobalConfiguration.INTERFACE_RELATIONSHIP_OUTPUT_PATH);
    }

}
