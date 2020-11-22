package tabby.dal.cache;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.util.NumberedString;
import tabby.config.GlobalConfiguration;
import tabby.dal.bean.ref.ClassReference;
import tabby.dal.bean.ref.MethodReference;
import tabby.dal.bean.ref.handle.ClassRefHandle;
import tabby.dal.service.ClassRefService;
import tabby.dal.service.MethodRefService;
import tabby.util.CSVUtils;
import tabby.util.ClassLoaderUtils;
import tabby.util.ClassResourceEnumerator;
import tabby.util.FileUtils;

import java.io.*;
import java.util.*;

import static tabby.config.GlobalConfiguration.*;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 * 这里实际选择用内存作为cache
 */
@Slf4j
@Data
@Component
public class CacheHelperImpl implements CacheHelper{

    @Autowired
    private ClassRefService classRefService;
    @Autowired
    private MethodRefService methodRefService;

    private List<String> runtimeClasses = new ArrayList<>();
    // 临时保存类信息和函数信息，这些数据结构仅在第一阶段发生改变
    // 第二阶段的数据流分析中仅添加函数信息中的call信息
    private Map<ClassRefHandle, ClassReference> savedClassRefs = new HashMap<>();
    private Map<String, MethodReference> savedMethodRefs = new HashMap<>();

    @Override
    public <T> void add(T ref) {
        if(ref instanceof ClassReference){
            ClassReference classRef = (ClassReference) ref;
            savedClassRefs.put(classRef.getHandle(), classRef);
        }else if(ref instanceof MethodReference){
            MethodReference methodRef = (MethodReference) ref;
            savedMethodRefs.put(methodRef.getSignature(), methodRef);
        }
    }

    @Override
    public <T> void remove(T ref) {
        if(ref instanceof ClassReference){
            ClassReference classRef = (ClassReference) ref;
            savedClassRefs.remove(classRef.getHandle());
        }else if(ref instanceof MethodReference){
            MethodReference methodRef = (MethodReference) ref;
            savedMethodRefs.remove(methodRef.getSignature());
        }
    }

    @Override
    public void clear(String type) {
        if("class".equals(type)){
            savedClassRefs.clear();
        }else if("method".equals(type)){
            savedMethodRefs.clear();
        }else{
            savedMethodRefs.clear();
            savedClassRefs.clear();
            runtimeClasses.clear();
        }
    }

    @Override
    public <T> void update(T ref) {
        if(ref instanceof ClassReference){
            ClassReference classRef = (ClassReference) ref;
            if(savedClassRefs.containsKey(classRef.getHandle())){
                savedClassRefs.replace(classRef.getHandle(), classRef);
                return;
            }
        }else if(ref instanceof MethodReference){
            MethodReference methodRef = (MethodReference) ref;
            if(savedMethodRefs.containsKey(methodRef.getSignature())){
                savedMethodRefs.replace(methodRef.getSignature(), methodRef);
                return;
            }
        }
        // 不存在相应的key，选择新增
        add(ref);
    }

    @Override
    public void loadRuntimeClasses(List<String> jars, boolean fileFirst){
        if(fileFirst && FileUtils.fileExists(RUNTIME_CACHE_PATH)){
            // load from file
            loadFromFile(RUNTIME_CACHE_PATH);
        }

        if(runtimeClasses == null || runtimeClasses.isEmpty()){
            // init runtime classes
            try {
                ClassResourceEnumerator classResourceEnumerator =
                        new ClassResourceEnumerator(ClassLoaderUtils.getClassLoader(jars));
                runtimeClasses = (List<String>) classResourceEnumerator.getTargetClassLoaderClasses();
                if(!runtimeClasses.isEmpty()){
                    save(RUNTIME_CACHE_PATH, runtimeClasses);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void save(String path, Object data) {
        try{
            File file = new File(path);
            if(!file.exists()){
                file.createNewFile();
            }
            FileWriter fileWritter = new FileWriter(file,true);
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            bufferWritter.write(GlobalConfiguration.GSON.toJson(data));
            bufferWritter.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void saveToNeo4j(){
        classRefService.clear(); // TODO 初始化图数据库 正式版去掉

        log.info("Load "+ getSavedMethodRefs().size()+ " method reference cache");
        methodRefService.importMethodRef();
        log.info("Load "+ getSavedClassRefs().size() +" class reference cache");
        classRefService.importClassRef();
        classRefService.buildEdge();
    }

    @Override
    public void saveToCSV() {
        try {
            Collection<List<String>> classRefs = new ArrayList<>();
            Collection<List<String>> methodRefs = new ArrayList<>();
            Collection<List<String>> extendEdges = new ArrayList<>();
            Collection<List<String>> hasEdges = new ArrayList<>();
            Collection<List<String>> interfacesEdges = new ArrayList<>();
            Collection<List<String>> callEdges = new ArrayList<>();
            Collection<List<String>> aliasEdges = new ArrayList<>();
            savedClassRefs.forEach((handle, classRef) -> {
                classRefs.add(classRef.toCSV());
                if(classRef.isHasSuperClass() && classRef.getExtendEdge() != null){
                    extendEdges.add(classRef.getExtendEdge().toCSV());
                }
                if(classRef.isHasInterfaces()){
                    classRef.getInterfaceEdge().forEach(interfaces -> {
                        interfacesEdges.add(interfaces.toCSV());
                    });
                }
                classRef.getHasEdge().forEach(has -> {
                    hasEdges.add(has.toCSV());
                });
            });
            savedMethodRefs.forEach((signature, methodRef) -> {
                methodRefs.add(methodRef.toCSV());
                methodRef.getCallEdge().forEach(call -> {
                    callEdges.add(call.toCSV());
                });
                if(methodRef.getAliasEdge() != null){
                    aliasEdges.add(methodRef.getAliasEdge().toCSV());
                }
            });
            CSVUtils.save(CLASSES_CACHE_PATH, CSV_HEADERS.get(0), classRefs);
            CSVUtils.save(METHODS_CACHE_PATH, CSV_HEADERS.get(1), methodRefs);
            CSVUtils.save(EXTEND_RELATIONSHIP_CACHE_PATH, CSV_HEADERS.get(2), extendEdges);
            CSVUtils.save(HAS_RELATIONSHIP_CACHE_PATH, CSV_HEADERS.get(3), hasEdges);
            CSVUtils.save(INTERFACE_RELATIONSHIP_CACHE_PATH, CSV_HEADERS.get(2), interfacesEdges);
            CSVUtils.save(CALL_RELATIONSHIP_CACHE_PATH, CSV_HEADERS.get(4), callEdges);
            CSVUtils.save(ALIAS_RELATIONSHIP_CACHE_PATH, CSV_HEADERS.get(5), aliasEdges);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public void loadFromFile(String path) {
        // TODO 从文件中恢复以前的解析内容
        File file = new File(path);
        if(!file.exists()) return;
        FileReader reader = null;
        try{
            reader = new FileReader(file);
            if(path.contains("runtime.json")){
                runtimeClasses = GlobalConfiguration.GSON.fromJson(reader, List.class);
            }else if(path.contains("class.dat")){
                // do nothing
//                savedClassRefs = GlobalConfiguration.GSON.fromJson(reader, Map.class);
            }else if(path.contains("method.dat")){
                // do nothing
//                savedMethodRefs = GlobalConfiguration.GSON.fromJson(reader, Map.class);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public ClassReference loadClassRef(String name) {
        ClassRefHandle handle = new ClassRefHandle(name);
        return loadClassRefByHandle(handle);
    }

    @Override
    public ClassReference loadClassRefByHandle(ClassRefHandle handle) {
        return savedClassRefs.getOrDefault(handle, null);
    }

    @Override
    public MethodReference loadMethodRef(String signature) {
        return savedMethodRefs.getOrDefault(signature, null);
    }

    /**
     * 当前函数解决soot调用函数不准确的问题
     * soot的invoke表达式会将父类、接口等函数都归宿到当前类函数上，导致无法找到相应的methodRef
     * 解决这个问题，通过往父类、接口找相应的内容
     * 这里找到的是第一个找到的函数
     * @param sootMethodRef
     * @return
     */
    @Override
    public MethodReference loadMethodRef(SootMethodRef sootMethodRef){
        MethodReference target = loadMethodRef(sootMethodRef.getSignature());
        if(target != null){
            return target;
        }

        return loadMethodRefFromFatherNodes(sootMethodRef);
    }

    @Override
    public MethodReference loadMethodRefFromFatherNodes(SootMethodRef sootMethodRef){
        MethodReference target = null;
        SootClass cls = sootMethodRef.getDeclaringClass();
        SootClass tmpCls = cls;
        while(tmpCls.hasSuperclass()){
            SootClass superCls = tmpCls.getSuperclass();
            target = findMethodRef(superCls, sootMethodRef.getSubSignature());
            if(target != null){
                return target;
            }
            tmpCls = superCls;
        }
        if(cls.getInterfaceCount() > 0){
            return findMethodRefFromInterfaces(cls, sootMethodRef.getSubSignature());
        }
        return null;
    }

    private MethodReference findMethodRefFromInterfaces(SootClass cls, NumberedString subSignature){
        MethodReference target = null;
        for(SootClass interCls:cls.getInterfaces()){
            target = findMethodRef(interCls, subSignature);
            if(target != null){
                return target;
            }else if(interCls.getInterfaceCount() > 0){
                target = findMethodRefFromInterfaces(interCls, subSignature);
                if(target != null){
                    return target;
                }
            }
        }
        return null;
    }

    private MethodReference findMethodRef(SootClass cls, NumberedString subSignature){
        MethodReference target = null;
        try{
            SootMethod targetMethod = cls.getMethod(subSignature);
            target = loadMethodRef(targetMethod.getSignature());
            if(target != null){
                return target;
            }
        }catch (RuntimeException e){
//                e.printStackTrace();
            // 当前类没找到函数，继续往父类找
        }
        return null;
    }
}
