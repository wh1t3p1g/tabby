package tabby.dal.cache;

import soot.SootMethodRef;
import tabby.dal.bean.ref.ClassReference;
import tabby.dal.bean.ref.MethodReference;
import tabby.dal.bean.ref.handle.ClassRefHandle;

import java.util.List;
import java.util.Map;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
public interface CacheHelper {

    <T> void add(T ref);

    <T> void remove(T ref);

    void clear(String type);

    <T> void update(T ref);

    void save(String path, Object data);

    void saveToCSV();
    void saveToNeo4j();

    void loadFromFile(String path);

    ClassReference loadClassRef(String name);

    ClassReference loadClassRefByHandle(ClassRefHandle handle);

    MethodReference loadMethodRef(String signature);

    MethodReference loadMethodRef(SootMethodRef sootMethodRef);

    void loadRuntimeClasses(List<String> jars, boolean fileFirst);

    List<String> getRuntimeClasses();

    Map<ClassRefHandle, ClassReference> getSavedClassRefs();

    Map<String, MethodReference> getSavedMethodRefs();
}
