package tabby.neo4j.bean.ref.handle;

import java.util.Objects;

/**
 * @author wh1t3P1g
 * @since 2020/11/4
 */
public class MethodRefHandle {
    private final ClassRefHandle classReference;
    private final String name;
    private final String signature;

    public MethodRefHandle(ClassRefHandle classReference, String name, String signature) {
        this.classReference = classReference;
        this.name = name;
        this.signature = signature;
    }

    public ClassRefHandle getClassReference() {
        return classReference;
    }

    public String getName() {
        return name;
    }

    public String getSignature() {
        return signature;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MethodRefHandle handle = (MethodRefHandle) o;

        if (!Objects.equals(classReference, handle.classReference))
            return false;
        if (!Objects.equals(name, handle.name)) return false;
        return Objects.equals(signature, handle.signature);
    }

    @Override
    public int hashCode() {
        int result = classReference != null ? classReference.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (signature != null ? signature.hashCode() : 0);
        return result;
    }
}
