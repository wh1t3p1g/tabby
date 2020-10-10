package tabby.bean.ref;

import lombok.Data;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import tabby.bean.edge.Call;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author wh1t3P1g
 * @since 2020/10/9
 */
@Data
@NodeEntity(label="Method")
public class MethodReference {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private String signature;

    private boolean isStatic;

    private ClassReference.Handle classRef;

    @Relationship(type="CALL", direction = "UNDIRECTED")
    private Set<Call> callEdge = new HashSet<>();

    public Handle getHandle() {
        return new Handle(classRef, name, signature);
    }

    public static class Handle {
        private final ClassReference.Handle classReference;
        private final String name;
        private final String signature;

        public Handle(ClassReference.Handle classReference, String name, String signature) {
            this.classReference = classReference;
            this.name = name;
            this.signature = signature;
        }

        public ClassReference.Handle getClassReference() {
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

            Handle handle = (Handle) o;

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

    // 后续添加分析后的数据字段
    public static MethodReference newInstance(String name, String signature){
        MethodReference methodRef = new MethodReference();
        methodRef.setName(name);
        methodRef.setSignature(signature);
        return methodRef;
    }
}
