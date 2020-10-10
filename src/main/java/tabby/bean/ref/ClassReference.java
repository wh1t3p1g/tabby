package tabby.bean.ref;

import lombok.Data;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import tabby.bean.edge.Extend;
import tabby.bean.edge.Has;
import tabby.bean.edge.Interfaces;

import java.util.ArrayList;
import java.util.HashSet;
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

    private List<String> interfaces = new ArrayList<>();

    private boolean isInterface;

    /**
     * [[name, modifiers, type],...]
     */
    private Set<List<String>> fields = new HashSet<>();

    private Set<String> annotations = new HashSet<>();

    // neo4j relationships
    /**
     * 继承边
     */
    @Relationship(type="EXTEND")
    private Extend extendEdge = null;

    /**
     * 类成员函数 has边
     * Object A has Method B
     */
    @Relationship(type="HAS", direction = "UNDIRECTED")
    private List<Has> hasEdge = new ArrayList<>();

    /**
     * 接口继承边
     * 双向，可以从实现类A到接口B，也可以从接口类B到实现类A
     * A -[:INTERFACE]-> B
     * B -[:INTERFACE]-> A
     */
    @Relationship(type="INTERFACE", direction = "UNDIRECTED")
    private Set<Interfaces> interfaceEdge = new HashSet<>();

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

    public Handle getHandle(){
        return new Handle(this.name);
    }

    public static ClassReference newInstance(String name){
        ClassReference classRef = new ClassReference();
        classRef.setName(name);
        classRef.setInterfaces(new ArrayList<>());
        classRef.setFields(new HashSet<>());
        classRef.setAnnotations(new HashSet<>());
        return classRef;
    }
}
