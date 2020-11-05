package tabby.dal.cache;

import tabby.dal.bean.ref.ClassReference;
import tabby.dal.bean.ref.MethodReference;
import tabby.dal.bean.ref.handle.ClassRefHandle;
import tabby.dal.bean.ref.handle.MethodRefHandle;

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

    void loadFromFile(String path);

    ClassReference loadClassRef(String name);

    ClassReference loadClassRefByHandle(ClassRefHandle handle);

    MethodReference loadMethodRef(ClassRefHandle classReference, String name, String signature);

    MethodReference loadMethodRefByHandle(MethodRefHandle handle);

    void loadRuntimeClasses(List<String> jdk);

    List<String> getRuntimeClasses();

    Map<ClassRefHandle, ClassReference> getSavedClassRefs();

    Map<MethodRefHandle, MethodReference> getSavedMethodRefs();
}
