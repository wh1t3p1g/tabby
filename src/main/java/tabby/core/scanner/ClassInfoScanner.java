package tabby.core.scanner;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.*;
import tabby.core.collector.ClassInfoCollector;
import tabby.core.container.DataContainer;
import tabby.dal.caching.bean.edge.Alias;
import tabby.dal.caching.bean.edge.Extend;
import tabby.dal.caching.bean.edge.Has;
import tabby.dal.caching.bean.edge.Interfaces;
import tabby.dal.caching.bean.ref.ClassReference;
import tabby.dal.caching.bean.ref.MethodReference;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
    private ClassInfoCollector collector;

    public void run(List<String> paths){
        // 多线程提取基础信息
        Map<String, CompletableFuture<ClassReference>> classes = loadAndExtract(paths);
        transform(classes.values()); // 等待收集结束，并保存classRef
        List<String> runtimeClasses = new ArrayList<>(classes.keySet());
        classes.clear();
        // 单线程提取关联信息
        buildClassEdges(runtimeClasses);
        save();
    }

    public Map<String, CompletableFuture<ClassReference>> loadAndExtract(List<String> targets){
        Map<String, CompletableFuture<ClassReference>> results = new HashMap<>();
        Scene.v().loadBasicClasses();

        Scene.v().loadDynamicClasses();
        int counter = 0;
        log.info("Start to collect {} targets' class information.", targets.size());
        for (final String path : targets) {
            for (String cl : SourceLocator.v().getClassesUnder(path)) {
                try{
                    SootClass theClass = Scene.v().loadClassAndSupport(cl);
                    if (!theClass.isPhantom()) {
                        // 这里存在类数量不一致的情况，是因为存在重复的对象
                        results.put(cl, collector.collect(theClass));
                        theClass.setApplicationClass();
                        if(counter % 10000 == 0){
                            log.info("Collected {} classes.", counter);
                        }
                        counter++;
                    }
                }catch (Exception e){
                    log.error("Load Error: " + e.getMessage());
//                    e.printStackTrace();
                }
            }
        }
        log.info("Collected {} classes.", counter);
        return results;
    }

    public void transform(Collection<CompletableFuture<ClassReference>> futures){
        for(CompletableFuture<ClassReference> future:futures){
            try {
                ClassReference classRef = future.get();

                dataContainer.store(classRef);
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                // 异步获取出错
            }
        }
    }

    public void buildClassEdges(List<String> classes){
        int counter = 0;
        int total = classes.size();
        log.info("Build {} classes' edges.", total);
        for(String cls:classes){
            if(counter%10000 == 0){
                log.info("Build {}/{} classes.", counter, total);
            }
            counter++;
            ClassReference clsRef = dataContainer.getClassRefByName(cls);
            if(clsRef == null) continue;
            extractRelationships(clsRef, dataContainer, 0);
        }
        log.info("Build {}/{} classes.", counter, total);
    }

    public static void extractRelationships(ClassReference clsRef, DataContainer dataContainer, int depth){
        // 建立继承关系
        if(clsRef.isHasSuperClass()){
            ClassReference superClsRef = dataContainer.getClassRefByName(clsRef.getSuperClass());
            if(superClsRef == null && depth < 10){ // 正常情况不会进入这个阶段
                superClsRef = collect0(clsRef.getSuperClass(), null, dataContainer, depth+1);
            }
            if(superClsRef != null){
                Extend extend =  Extend.newInstance(clsRef, superClsRef);
                clsRef.setExtendEdge(extend);
                dataContainer.store(extend);
            }
        }

        // 建立接口关系
        if(clsRef.isHasInterfaces()){
            List<String> infaces = clsRef.getInterfaces();
            for(String inface:infaces){
                ClassReference infaceClsRef = dataContainer.getClassRefByName(inface);
                if(infaceClsRef == null && depth < 10){// 正常情况不会进入这个阶段
                    infaceClsRef = collect0(inface, null, dataContainer, depth+1);
                }
                if(infaceClsRef != null){
                    Interfaces interfaces = Interfaces.newInstance(clsRef, infaceClsRef);
                    clsRef.getInterfaceEdge().add(interfaces);
                    dataContainer.store(interfaces);
                }
            }
        }
        // 建立函数别名关系
        makeAliasRelations(clsRef, dataContainer);
    }

    /**
     * 根据单个类进行类信息收集
     * @param classname 待收集的类名
     * @return 具体的类信息
     */
    public static ClassReference collect0(String classname, SootClass cls,
                                          DataContainer dataContainer, int depth){
        ClassReference classRef = null;
        try{
            if(cls == null){
                cls = Scene.v().getSootClass(classname);
            }
        }catch (Exception e){
            // class not found
        }

        if(cls != null) {
            if(cls.isPhantom()){
                classRef = ClassReference.newInstance(cls);
                classRef.setPhantom(true);
            }else{
                classRef = ClassInfoCollector.collect0(cls, dataContainer);

                extractRelationships(classRef, dataContainer, depth);
            }
        }else if(!classname.isEmpty()){
            classRef = ClassReference.newInstance(classname);
            classRef.setPhantom(true);
        }

        dataContainer.store(classRef);
        return classRef;
    }



    public static void makeAliasRelations(ClassReference ref, DataContainer dataContainer){
        if(ref == null)return;
        // build alias relationship
        if(ref.getHasEdge() == null) return;

        List<Has> hasEdges = ref.getHasEdge();
        for(Has has:hasEdges){
            makeAliasRelation(has, dataContainer);
        }

        ref.setInitialed(true);
    }

    public static void makeAliasRelation(Has has, DataContainer dataContainer){
        MethodReference currentMethodRef = has.getMethodRef();

        if("<init>".equals(currentMethodRef.getName())
                || "<clinit>".equals(currentMethodRef.getName())){
            return;
        }

        SootMethod currentSootMethod = currentMethodRef.getMethod();
        if(currentSootMethod == null) return;

        SootClass cls = currentSootMethod.getDeclaringClass();
        MethodReference fatherNodeMethodRef
                = dataContainer.getFirstMethodRefFromFatherNodes(
                cls, currentSootMethod.getSubSignature(), false);

        if(fatherNodeMethodRef != null){
            Alias alias = Alias.newInstance(fatherNodeMethodRef, currentMethodRef);
            fatherNodeMethodRef.getChildAliasEdges().add(alias);
//            currentMethodRef.setAliasEdge(alias);
            dataContainer.store(alias);
        }
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