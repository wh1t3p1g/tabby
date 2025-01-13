package tabby.core.collector;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import soot.SootClass;
import soot.SootMethod;
import tabby.common.bean.edge.Alias;
import tabby.common.bean.edge.Extend;
import tabby.common.bean.edge.Has;
import tabby.common.bean.edge.Interfaces;
import tabby.common.bean.ref.ClassReference;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.SemanticUtils;
import tabby.config.GlobalConfiguration;
import tabby.core.container.DataContainer;
import tabby.core.container.RulesContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * @author wh1t3p1g
 * @since 2021/8/31
 */
@Service
public class ClassInfoCollector {

    @Autowired
    private DataContainer dataContainer;

    @Autowired
    RulesContainer rulesContainer;

    @Async("tabby-collector")
    public CompletableFuture<ClassReference> collect(SootClass cls) {
        // generate class reference
        ClassReference classRef = ClassReference.newInstance(cls);
        // generate method reference
        collectMethodsInfo(classRef, cls, dataContainer, rulesContainer, false);
        return CompletableFuture.completedFuture(classRef);
    }

    public static void collectMethodsInfo(ClassReference classRef, SootClass cls,
                                          DataContainer dataContainer, RulesContainer rulesContainer, boolean newAdded) {
        if (cls == null || cls.isPhantom()) {
            cls = SemanticUtils.getSootClass(classRef.getName());
        }

        // 提取类函数信息
        if (cls != null && cls.getMethodCount() > 0) {
            List<SootMethod> methods = new ArrayList<>(cls.getMethods());
            for (SootMethod method : methods) {
                classRef.getMethodSubSignatures().add(method.getSubSignature());
                // check from db
                MethodReference methodRef = dataContainer.getMethodRefBySignature(method.getSignature(), false);
                if (methodRef != null) continue;
                collectSingleMethodRef(classRef, method, newAdded, false, dataContainer, rulesContainer);
            }
        }
    }

    public static MethodReference collectSingleMethodRef(ClassReference classRef, SootMethod method, boolean newAdded, boolean genAlias,
                                                         DataContainer dataContainer, RulesContainer rulesContainer) {
        String classname = classRef.getName();
        MethodReference methodRef = MethodReference.newInstance(classname, method);
        // apply rules
        if (rulesContainer == null) {
            rulesContainer = GlobalConfiguration.rulesContainer;
        }
        rulesContainer.apply(classRef, methodRef);
        rulesContainer.applyTagRule(classRef, methodRef);
        // add to new added for next stage
        if (GlobalConfiguration.IS_NEED_TO_DEAL_NEW_ADDED_METHOD && newAdded) {
            dataContainer.getNewAddedMethodSigs().add(methodRef.getSignature());
        }
        // record on-fly methods
        if (GlobalConfiguration.IS_ON_DEMAND_DRIVE && !"normal".equals(methodRef.getType())) {
            dataContainer.getOnDemandMethods().add(methodRef.getSignature());
        }
        // generate has edge
        generateHasEdge(classRef, methodRef, dataContainer, newAdded);
        dataContainer.store(methodRef, false);
        if (genAlias) {
            generateAliasEdge(methodRef, dataContainer);
        }
        return methodRef;
    }

    public static ClassReference collectAndSave(Object clazz, boolean newAdded,
                                                DataContainer dataContainer, RulesContainer rulesContainer) {
        SootClass cls = null;
        if (clazz instanceof SootClass) {
            cls = (SootClass) clazz;
        } else if (clazz instanceof String) {
            cls = SemanticUtils.getSootClass((String) clazz);
        }
        if (cls != null) {
            if (rulesContainer == null) {
                rulesContainer = GlobalConfiguration.rulesContainer;
            }
            // generate class reference
            ClassReference classRef = ClassReference.newInstance(cls);
            // generate method reference
            collectMethodsInfo(classRef, cls, dataContainer, rulesContainer, newAdded);
            // save
            dataContainer.store(classRef, false);
            return classRef;
        }
        return null;
    }

    public static void collectRuntimeForSingleClazz(Object clazz, boolean newAdded,
                                                    DataContainer dataContainer, RulesContainer rulesContainer) {
        ClassReference classReference = collectAndSave(clazz, newAdded, dataContainer, rulesContainer);
        collectRelationInfo(classReference, true, newAdded, dataContainer, rulesContainer);
    }

    public static void collectRelationInfo(ClassReference classRef, boolean isNeedFetchFromDatabase, boolean newAdded,
                                           DataContainer dataContainer, RulesContainer rulesContainer) {
        if (classRef == null) return;
        // 建立继承关系
        if (classRef.isHasSuperClass() && !"java.lang.Object".equals(classRef.getSuperClass())) {
            ClassReference superClsRef = dataContainer.getClassRefByName(classRef.getSuperClass(), isNeedFetchFromDatabase);

            if (superClsRef == null) {
                superClsRef = collectAndSave(classRef.getSuperClass(), newAdded, dataContainer, rulesContainer);
            }

            if (superClsRef != null) {
                Extend extend = Extend.newInstance(classRef, superClsRef);
                dataContainer.store(extend, true);
                superClsRef.getChildClassnames().add(classRef.getName());
            }
        }

        // 建立接口关系
        if (classRef.isHasInterfaces()) {
            List<String> infaces = classRef.getInterfaces();
            for (String inface : infaces) {
                ClassReference infaceClsRef = dataContainer.getClassRefByName(inface, isNeedFetchFromDatabase);
                if (infaceClsRef == null) {// 正常情况不会进入这个阶段
                    infaceClsRef = collectAndSave(inface, false, dataContainer, rulesContainer);
                }
                if (infaceClsRef != null) {
                    Interfaces interfaces = Interfaces.newInstance(classRef, infaceClsRef);
                    dataContainer.store(interfaces, true);
                    infaceClsRef.getChildClassnames().add(classRef.getName());
                }
            }
        }
        // 建立函数别名关系
        List<Has> hasEdges = classRef.getHasEdge();
        if (hasEdges != null && !hasEdges.isEmpty()) {
            for (Has has : hasEdges) {
                generateAliasEdge(has.getMethodRef(), dataContainer);
            }
        }
        // finished
        classRef.setInitialed(true);
    }

    public static void generateHasEdge(ClassReference classRef, MethodReference methodRef,
                                       DataContainer dataContainer, boolean newAdded) {
        Has has = Has.newInstance(classRef, methodRef);
        if (classRef.getHasEdge().contains(has)) return;

        classRef.getHasEdge().add(has);
        dataContainer.store(has, true);
        if (newAdded) {
            generateAliasEdge(methodRef, dataContainer);
        }
    }

    public static void generateAliasEdge(MethodReference methodRef, DataContainer dataContainer) {
        String methodName = methodRef.getName();

        if ("<init>".equals(methodName)
                || "<clinit>".equals(methodName)) {
            return;
        }

        SootMethod currentSootMethod = methodRef.getMethod();
        if (currentSootMethod == null) return;

        SootClass cls = currentSootMethod.getDeclaringClass();

        Set<MethodReference> refs =
                dataContainer.getAliasMethodRefs(cls, currentSootMethod.getSubSignature());

        if (refs != null && !refs.isEmpty()) {
            for (MethodReference ref : refs) {
                Alias alias = Alias.newInstance(ref, methodRef);
                ref.getChildAliasEdges().add(alias);
                dataContainer.store(alias, true);
            }
        }
    }
}