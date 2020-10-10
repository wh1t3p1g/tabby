package tabby.dal.cache;

import tabby.bean.ref.ClassReference;
import tabby.bean.ref.MethodReference;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
public interface CacheHelper {

    public <T> void add(T ref);

    public <T> void remove(T ref);

    public void clear(String type);

    public <T> void update(T ref);

    public void save(String path);

    public void loadFromFile(String path);

    public ClassReference loadClassRef(String name);

    public ClassReference loadClassRefByHandle(ClassReference.Handle handle);

    public MethodReference loadMethodRef(ClassReference.Handle classReference, String name, String signature);

    public MethodReference loadMethodRefByHandle(MethodReference.Handle handle);
}
