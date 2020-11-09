package tabby.core.scanner;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootClass;
import tabby.dal.bean.edge.Extend;
import tabby.dal.bean.edge.Interfaces;
import tabby.dal.bean.ref.ClassReference;
import tabby.dal.bean.ref.handle.ClassRefHandle;
import tabby.dal.cache.CacheHelper;
import tabby.dal.service.ClassRefService;
import tabby.dal.service.MethodRefService;

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
public class ClassInfoScanner {

    @Autowired
    private CacheHelper cacheHelper;
    @Autowired
    private ClassRefService classRefService;
    @Autowired
    private MethodRefService methodRefService;

    public void run(List<String> classes){
        collect(classes);
        build();
        save();
    }

    public void collect(List<String> classes){
        if(classes.isEmpty()) return;

        classes.forEach(this::collect);

        log.info("collect "+classes.size()+" classes information. DONE!");
    }

    public void build(){
        if(cacheHelper.getSavedClassRefs().isEmpty()) return;
        Map<ClassRefHandle, ClassReference> clonedClassRefs = new HashMap<>(cacheHelper.getSavedClassRefs());
        clonedClassRefs.forEach((handle, classRef) -> {
            // build superclass relationship
            if(classRef.isHasSuperClass()){
                ClassReference superClassRef = cacheHelper.loadClassRef(classRef.getSuperClass()); // 优先从cache中取
                if(superClassRef == null){ // cache中没有 默认为新类
                    superClassRef = collect(classRef.getSuperClass());
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
                    }
                    Interfaces interfaces = Interfaces.newInstance(classRef, interfaceClassRef);
                    classRef.getInterfaceEdge().add(interfaces);
                });
            }
        });
    }

    public void save(){
        log.info("start to save cache to neo4j database!");
        // clear cache runtime classes
//        cacheHelper.getRuntimeClasses().clear();
        classRefService.clear();
        // save cache to csv
        cacheHelper.saveToCSV();
        // load csv data to neo4j
        log.info("load "+ cacheHelper.getSavedMethodRefs().size()+ " method reference cache");
        methodRefService.importMethodRef();
        log.info("load "+ cacheHelper.getSavedClassRefs().size() +" class reference cache");
        classRefService.importClassRef();
        classRefService.buildEdge();
        log.info("load csv data to neo4j finished!");
    }

    private ClassReference collect(String classname){
        ClassReference classRef = null;
        try{
            SootClass sc = Scene.v().getSootClass(classname);
            if(!sc.isPhantom()) {
                classRef = ClassReference.parse(sc);
                cacheHelper.add(classRef);
                classRef.getHasEdge().forEach((has) -> {
                    cacheHelper.add(has.getMethodRef());
                });
            }
        }catch (Exception e){
            // class not found
            log.debug(classname+" class not found!");
        }
//        if(classRef == null){// 无法找到相应的类，只存储一个classname
        // // TODO 是否需要去存储没办法找到的类 存疑？
//            classRef = ClassReference.newInstance(classname);
//            cacheHelper.add(classRef);
//        }
        return classRef;
    }
}
