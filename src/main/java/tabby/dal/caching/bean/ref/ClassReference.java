package tabby.dal.caching.bean.ref;

import com.google.common.hash.Hashing;
import lombok.Data;
import org.springframework.data.annotation.Transient;
import tabby.dal.caching.bean.edge.Extend;
import tabby.dal.caching.bean.edge.Has;
import tabby.dal.caching.bean.edge.Interfaces;
import tabby.dal.caching.converter.List2JsonStringConverter;
import tabby.dal.caching.converter.Set2JsonStringConverter;

import javax.persistence.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/10/9
 */
@Data
@Entity
@Table(name = "classes")
public class ClassReference {

    @Id
    private String id;
//    @Column(unique = true)
    private String name;
    private String superClass;

    private boolean isInterface = false;
    private boolean hasSuperClass = false;
    private boolean hasInterfaces = false;
    private boolean isInitialed = false;
    private boolean isSerializable = false;
    /**
     * [[name, modifiers, type],...]
     */
    @Column(columnDefinition = "TEXT")
    @Convert(converter = Set2JsonStringConverter.class)
    private Set<String> fields = new HashSet<>();

    @Column(columnDefinition = "TEXT")
    @Convert(converter = List2JsonStringConverter.class)
    private List<String> interfaces = new ArrayList<>();

    // neo4j relationships
    /**
     * 继承边
     */
    @Transient
    private transient Extend extendEdge = null;

    /**
     * 类成员函数 has边
     * Object A has Method B
     */
    @Transient
    private transient List<Has> hasEdge = new ArrayList<>();

    /**
     * 接口继承边
     * 双向，可以从实现类A到接口B，也可以从接口类B到实现类A
     * A -[:INTERFACE]-> B
     * B -[:INTERFACE]-> A
     */
    @Transient
    private transient Set<Interfaces> interfaceEdge = new HashSet<>();

    public static ClassReference newInstance(String name){
        ClassReference classRef = new ClassReference();
        String id = Hashing.sha256() // 相同class生成的id值也相同
                .hashString(name, StandardCharsets.UTF_8)
                .toString();
        classRef.setId(id);
        classRef.setName(name);
        classRef.setInterfaces(new ArrayList<>());
        classRef.setFields(new HashSet<>());
        return classRef;
    }

}
