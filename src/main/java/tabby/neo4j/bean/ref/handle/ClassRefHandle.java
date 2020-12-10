package tabby.neo4j.bean.ref.handle;

/**
 * @author wh1t3P1g
 * @since 2020/11/4
 */
public class ClassRefHandle {

    private final String name;

    public ClassRefHandle(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ClassRefHandle handle = (ClassRefHandle) o;

        return name != null ? name.equals(handle.name) : handle.name == null;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}
