package tabby.dal.cache;

import tabby.bean.ref.ClassReference;
import tabby.bean.ref.MethodReference;

import java.util.HashMap;
import java.util.Map;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 * 这里实际选择用内存作为cache
 */
public class CacheHelperImpl implements CacheHelper{

    private Map<ClassReference.Handle, ClassReference> savedClassRefs = new HashMap<>();
    private Map<MethodReference.Handle, MethodReference> savedMethodRefs = new HashMap<>();

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
    public void save(String path) {
        // TODO 保存到文件
    }

    @Override
    public void loadFromFile(String path) {
        // TODO 从文件中恢复以前的解析内容
    }

    @Override
    public ClassReference loadClassRef(String name) {
        ClassReference.Handle handle = new ClassReference.Handle(name);
        return loadClassRefByHandle(handle);
    }

    @Override
    public ClassReference loadClassRefByHandle(ClassReference.Handle handle) {
        return savedClassRefs.get(handle);
    }

    @Override
    public MethodReference loadMethodRef(ClassReference.Handle classReference, String name, String signature) {
        MethodReference.Handle handle = new MethodReference.Handle(classReference, name, signature);
        return loadMethodRefByHandle(handle);
    }

    @Override
    public MethodReference loadMethodRefByHandle(MethodReference.Handle handle) {
        return savedMethodRefs.get(handle);
    }
}
