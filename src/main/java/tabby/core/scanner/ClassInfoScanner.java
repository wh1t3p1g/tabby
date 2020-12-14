package tabby.core.scanner;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import tabby.core.data.RulesContainer;
import tabby.neo4j.bean.edge.Alias;
import tabby.neo4j.bean.edge.Extend;
import tabby.neo4j.bean.edge.Interfaces;
import tabby.neo4j.bean.ref.ClassReference;
import tabby.neo4j.bean.ref.MethodReference;
import tabby.neo4j.bean.ref.handle.ClassRefHandle;
import tabby.neo4j.cache.CacheHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 处理jdk相关类的信息抽取
 * @author wh1t3P1g
 * @since 2020/10/21
 */
@Data
@Slf4j
@Component
public class ClassInfoScanner implements Scanner<List<String>> {

    @Autowired
    private CacheHelper cacheHelper;
    @Autowired
    private RulesContainer rulesContainer;

    @Override
    public void run(List<String> classes){
        collect(classes);
        build();
    }

    @Override
    public void collect(List<String> classes){
        if(classes.isEmpty()) return;

        classes.forEach(this::collect);

        log.info("Collect "+classes.size()+" classes information. DONE!");
    }

    @Override
    public void build(){
        if(cacheHelper.getSavedClassRefs().isEmpty()) return;
        Map<ClassRefHandle, ClassReference> clonedClassRefs = new HashMap<>(cacheHelper.getSavedClassRefs());
        clonedClassRefs.forEach((handle, classRef) -> {
            buildRelationships(classRef);
        });
    }

    private void buildRelationships(ClassReference classRef){
        if(classRef == null) return;
        if(classRef.isInitialed())return;
        // build superclass relationship
        if(classRef.isHasSuperClass()){
            ClassReference superClassRef = cacheHelper.loadClassRef(classRef.getSuperClass()); // 优先从cache中取
            if(superClassRef == null){ // cache中没有 默认为新类
//                if(classRef.getSuperClass().toString().equals("java.lang.ClassLoader")){
//                    System.out.println(1);
//                }
                superClassRef = collect(classRef.getSuperClass());
                if(superClassRef == null){
                    System.out.println(1);
                }
                buildRelationships(superClassRef);
            }
            Extend extend =  Extend.newInstance(classRef, superClassRef);
            classRef.setExtendEdge(extend);
        }
        // build interfaces relationship
        if(classRef.isHasInterfaces()){
            classRef.getInterfaces().forEach((intface) -> {
                ClassReference interfaceClassRef = cacheHelper.loadClassRef(intface);
                if(interfaceClassRef == null){
                    interfaceClassRef = collect(intface);
                    buildRelationships(interfaceClassRef);
                }
                Interfaces interfaces = Interfaces.newInstance(classRef, interfaceClassRef);
                classRef.getInterfaceEdge().add(interfaces);
            });
        }

        // build alias relationship
        classRef.getHasEdge().forEach(has -> {
            MethodReference sourceRef = has.getMethodRef();
            SootMethod sootMethod = sourceRef.getCachedMethod();
            SootMethodRef sootMethodRef = sootMethod.makeRef();
            MethodReference targetRef = cacheHelper.loadMethodRefFromFatherNodes(sootMethodRef);
            if(targetRef != null){
                Alias alias = Alias.newInstance(sourceRef, targetRef);
                sourceRef.setAliasEdge(alias);
            }
        });
        classRef.setInitialed(true);
    }

    @Override
    public void save(){
        log.info("Start to save cache to neo4j database!");
        // clear cache runtime classes
        cacheHelper.getRuntimeClasses().clear();
        // save cache to csv
        cacheHelper.saveToCSV();
        // load csv data to neo4j
        cacheHelper.saveToNeo4j();
        log.info("Load csv data to neo4j finished!");
    }

    /**
     * 根据单个类进行类信息收集
     * @param classname 待收集的类名
     * @return 具体的类信息
     */
    private ClassReference collect(String classname){
        ClassReference classRef = null;
        try{
            SootClass sc = Scene.v().getSootClass(classname);
            if(!sc.isPhantom()) {
                classRef = ClassReference.parse(sc, rulesContainer);
                cacheHelper.add(classRef);
                classRef.getHasEdge().forEach((has) -> {
                    cacheHelper.add(has.getMethodRef());
                });
            }
        }catch (Exception e){
            // class not found
//            e.printStackTrace();
//            log.debug(classname+" class not found!");
        }
        return classRef;
    }
}
