package tabby.dal.cache;

import com.google.gson.Gson;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tabby.dal.bean.ref.ClassReference;
import tabby.dal.bean.ref.MethodReference;
import tabby.dal.bean.ref.handle.ClassRefHandle;
import tabby.dal.bean.ref.handle.MethodRefHandle;
import tabby.util.CSVUtils;
import tabby.util.ClassLoaderUtils;
import tabby.util.ClassResourceEnumerator;

import java.io.*;
import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 * 这里实际选择用内存作为cache
 */
@Slf4j
@Data
@Component
public class CacheHelperImpl implements CacheHelper{

    private List<String> runtimeClasses = new ArrayList<>();
    // 临时保存类信息和函数信息，这些数据结构仅在第一阶段发生改变
    // 第二阶段的数据流分析中仅添加函数信息中的call信息
    private Map<ClassRefHandle, ClassReference> savedClassRefs = new HashMap<>();
    private Map<MethodRefHandle, MethodReference> savedMethodRefs = new HashMap<>();

    private static String CACHE_PATH = String.join(File.separator, System.getProperty("user.dir"), "cache");
    private static String RUNTIME_CACHE_PATH = String.join(File.separator, CACHE_PATH, "runtime.bat");
    private static String CLASSES_CACHE_PATH = String.join(File.separator,CACHE_PATH, "classes.csv");
    private static String METHODS_CACHE_PATH = String.join(File.separator,CACHE_PATH, "methods.csv");
    private static String CALL_RELATIONSHIP_CACHE_PATH = String.join(File.separator,CACHE_PATH, "calls.csv");
    private static String EXTEND_RELATIONSHIP_CACHE_PATH = String.join(File.separator,CACHE_PATH, "extends.csv");
    private static String HAS_RELATIONSHIP_CACHE_PATH = String.join(File.separator,CACHE_PATH, "has.csv");
    private static String INTERFACE_RELATIONSHIP_CACHE_PATH = String.join(File.separator,CACHE_PATH, "interfaces.csv");
    private static List<String[]> CSV_HEADERS = new ArrayList<>(Arrays.asList(
            new String[]{"uuid", "name", "superClass", "interfaces", "isInterface", "hasSuperClass", "hasInterfaces", "fields"},// class
            new String[]{"uuid", "name", "signature", "isStatic"},// method
            new String[]{"uuid", "source", "target"}, // extend/interfaces/
            new String[]{"uuid", "classRef", "MethodRef"}, // has
            new String[]{"uuid", "source", "target", "lineNum"} // call

    ));
//    private static List<>

    @Override
    public <T> void add(T ref) {
        if(ref instanceof ClassReference){
            ClassReference classRef = (ClassReference) ref;
            savedClassRefs.put(classRef.getHandle(), classRef);
        }else if(ref instanceof MethodReference){
            MethodReference methodRef = (MethodReference) ref;
            savedMethodRefs.put(methodRef.getHandle(), methodRef);
        }
    }

    @Override
    public <T> void remove(T ref) {
        if(ref instanceof ClassReference){
            ClassReference classRef = (ClassReference) ref;
            savedClassRefs.remove(classRef.getHandle());
        }else if(ref instanceof MethodReference){
            MethodReference methodRef = (MethodReference) ref;
            savedMethodRefs.remove(methodRef.getHandle());
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
            if(savedMethodRefs.containsKey(methodRef.getHandle())){
                savedMethodRefs.replace(methodRef.getHandle(), methodRef);
                return;
            }
        }
        // 不存在相应的key，选择新增
        add(ref);
    }

    @Override
    public void loadRuntimeClasses(List<String> jdk){
        String path = String.join(File.separator, System.getProperty("user.dir"), "cache", "runtime.dat");
        File file = new File(path);
        if(file.exists()){
            // load from file
            loadFromFile(path);
        }
        if(runtimeClasses == null || runtimeClasses.isEmpty()){
            // init runtime classes
            try {
                ClassResourceEnumerator classResourceEnumerator = new ClassResourceEnumerator(ClassLoaderUtils.getClassLoader(jdk));
                runtimeClasses = (List<String>) classResourceEnumerator.getTargetClassLoaderClasses();
                if(!runtimeClasses.isEmpty()){
                    save(path, runtimeClasses);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void save(String path, Object data) {
        // TODO 保存到文件
        log.info(path);
        try{
            File file = new File(path);
            if(!file.exists()){
                file.createNewFile();
            }
            FileWriter fileWritter = new FileWriter(file,true);
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            bufferWritter.write(new Gson().toJson(data));
            bufferWritter.close();
        }catch (Exception e){
            e.printStackTrace();
        }
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
            savedMethodRefs.forEach((handle, methodRef) -> {
                methodRefs.add(methodRef.toCSV());
                methodRef.getCallEdge().forEach(call -> {
                    callEdges.add(call.toCSV());
                });
            });
            CSVUtils.save(CLASSES_CACHE_PATH, CSV_HEADERS.get(0), classRefs);
            CSVUtils.save(METHODS_CACHE_PATH, CSV_HEADERS.get(1), methodRefs);
            CSVUtils.save(EXTEND_RELATIONSHIP_CACHE_PATH, CSV_HEADERS.get(2), extendEdges);
            CSVUtils.save(HAS_RELATIONSHIP_CACHE_PATH, CSV_HEADERS.get(3), hasEdges);
            CSVUtils.save(INTERFACE_RELATIONSHIP_CACHE_PATH, CSV_HEADERS.get(2), interfacesEdges);
            CSVUtils.save(CALL_RELATIONSHIP_CACHE_PATH, CSV_HEADERS.get(4), callEdges);
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
        Object obj = null;
        try{
            reader = new FileReader(file);
            Gson gson = new Gson();
            if(path.contains("runtime.dat")){
                runtimeClasses = gson.fromJson(reader, List.class);
            }else if(path.contains("class.dat")){
                savedClassRefs = gson.fromJson(reader, Map.class);
            }else if(path.contains("method.dat")){
                savedMethodRefs = gson.fromJson(reader, Map.class);
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
    public MethodReference loadMethodRef(ClassRefHandle classReference, String name, String signature) {
        MethodRefHandle handle = new MethodRefHandle(classReference, name, signature);
        return loadMethodRefByHandle(handle);
    }

    @Override
    public MethodReference loadMethodRefByHandle(MethodRefHandle handle) {
        return savedMethodRefs.getOrDefault(handle, null);
    }
}
