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

    public void collect(){
        if(cacheHelper.getRuntimeClasses().isEmpty()) return;

        cacheHelper.getRuntimeClasses().forEach(this::collect);

        log.info("collect jdk runtime classes information. DONE!");
    }

    public void build(){
        if(cacheHelper.getSavedClassRefs().isEmpty()) return;
        Map<ClassRefHandle, ClassReference> clonedClassRefs = new HashMap<>();
        clonedClassRefs.putAll(cacheHelper.getSavedClassRefs());
        clonedClassRefs.forEach((handle, classRef) -> {
            // build superclass relationship
            if(classRef.isHasSuperClass()){
                ClassReference superClassRef = cacheHelper.loadClassRef(classRef.getSuperClass());
                if(superClassRef == null){
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
        cacheHelper.getRuntimeClasses().clear();
        classRefService.clear();
        // save cache to neo4j database
        if(!cacheHelper.getSavedClassRefs().isEmpty()){
//            cacheHelper.getSavedClassRefs().forEach((key, value) -> {
//                classRefService.save(value);
//            });
            cacheHelper.saveToCSV();
//            classRefService.saveAll(cacheHelper.getSavedClassRefs().values());
        }

        if(!cacheHelper.getSavedMethodRefs().isEmpty()){

        }
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
//            log.error(classname+" class not found!");
        }
        return classRef;
    }
}
