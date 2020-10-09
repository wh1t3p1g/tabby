package tabby.bean;

import lombok.Data;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;

import java.util.List;
import java.util.Set;

/**
 * @author wh1t3P1g
 * @since 2020/10/9
 */
@Data
@NodeEntity(label="Class")
public class ClassReference {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private String superClass;

    private List<String> interfaces;

    private boolean isInterface;

    private Set<FieldReference> fields;

    private Set<String> annotations;

    /**
     * 这里采用跟gadgetinspector一样的策略，减少ClassReference在其他类中的占用内容，节省空间
     */
    public static class Handle {
        private final String name;

        public Handle(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Handle handle = (Handle) o;

            return name != null ? name.equals(handle.name) : handle.name == null;
        }

        @Override
        public int hashCode() {
            return name != null ? name.hashCode() : 0;
        }
    }
}
