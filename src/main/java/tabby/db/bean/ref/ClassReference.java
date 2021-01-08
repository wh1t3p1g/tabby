package tabby.db.bean.ref;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import org.springframework.data.annotation.Transient;
import tabby.db.bean.edge.Extend;
import tabby.db.bean.edge.Has;
import tabby.db.bean.edge.Interfaces;

import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/10/9
 */
@Getter
@Setter
@NodeEntity(label="Class")
public class ClassReference{

    @Id
    private String id;

    private String name;

    private boolean isInterface = false;
    private boolean hasSuperClass = false;
    private boolean hasInterfaces = false;
    private boolean isInitialed = false;
    /**
     * [[name, modifiers, type],...]
     */
    private Set<String> fields = new HashSet<>();
    private String superClass;
    private List<String> interfaces = new ArrayList<>();


    // neo4j relationships
    /**
     * 继承边
     */
    @Transient // for mongodb
    @Relationship(type="EXTEND")
    private Extend extendEdge = null;

    /**
     * 类成员函数 has边
     * Object A has Method B
     */
    @Transient // for mongodb
    @Relationship(type="HAS", direction = "UNDIRECTED")
    private List<Has> hasEdge = new ArrayList<>();

    /**
     * 接口继承边
     * 双向，可以从实现类A到接口B，也可以从接口类B到实现类A
     * A -[:INTERFACE]-> B
     * B -[:INTERFACE]-> A
     */
    @Transient // for mongodb
    @Relationship(type="INTERFACE", direction = "UNDIRECTED")
    private Set<Interfaces> interfaceEdge = new HashSet<>();

    public static ClassReference newInstance(String name){
        ClassReference classRef = new ClassReference();
        classRef.setId(UUID.randomUUID().toString());
        classRef.setName(name);
        classRef.setInterfaces(new ArrayList<>());
        classRef.setFields(new HashSet<>());
//        classRef.setAnnotations(new HashSet<>());
        return classRef;
    }

    public List<String> toCSV(){
        List<String> ret = new ArrayList<>();
        ret.add(id);
        ret.add(name);
        ret.add(superClass);
        ret.add(String.join("|", interfaces));
        ret.add(Boolean.toString(isInterface));
        ret.add(Boolean.toString(hasSuperClass));
        ret.add(Boolean.toString(hasInterfaces));
        ret.add(String.join("|", fields));
        return ret;
    }

}
