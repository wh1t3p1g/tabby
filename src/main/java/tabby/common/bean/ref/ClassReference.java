package tabby.common.bean.ref;

import com.google.common.hash.Hashing;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.Transient;
import soot.SootClass;
import soot.SootField;
import tabby.common.bean.converter.List2JsonStringConverter;
import tabby.common.bean.converter.Map2JsonStringForAnnotationsConverter;
import tabby.common.bean.converter.Set2JsonStringConverter;
import tabby.common.bean.edge.Has;
import tabby.common.utils.SemanticUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/10/9
 */
@Data
@Entity
@Table(name = "classes", indexes = {@Index(columnList = "name")})
public class ClassReference {

    @Id
    private String id;
    //    @Column(unique = true)
    private String name;
    private String superClass;

    private boolean isPublic = false;
    private boolean isPhantom = false;
    private boolean isAbstract = false;
    private boolean isInterface = false;
    private boolean hasSuperClass = false;
    private boolean hasInterfaces = false;
    private boolean hasDefaultConstructor = false;
    private boolean isInitialed = false;
    private boolean isSerializable = false;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = List2JsonStringConverter.class)
    private List<String> interfaces = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    @Convert(converter = List2JsonStringConverter.class)
    private List<String> childClassnames = new ArrayList<>();


    @Column(columnDefinition = "TEXT")
    @Convert(converter = Set2JsonStringConverter.class)
    private Set<String> methodSubSignatures = new HashSet<>();

    @Transient
//    @Column(columnDefinition = "TEXT")
//    @Convert(converter = MapString2JsonStringConverter.class)
    private transient Map<String, String> fields = new HashMap<>();

    @Column(columnDefinition = "TEXT")
    @Convert(converter = Map2JsonStringForAnnotationsConverter.class)
    private Map<String, Map<String, Set<String>>> annotations = new HashMap<>();

    // neo4j relationships
    /**
     * 类成员函数 has边
     * Object A has Method B
     */
    @Transient
    private transient List<Has> hasEdge = new ArrayList<>();

    public static ClassReference newInstance(String name) {
        ClassReference classRef = new ClassReference();
        String id = Hashing.md5() // 相同class生成的id值也相同
                .hashString(name, StandardCharsets.UTF_8)
                .toString();
        classRef.setId(id);
        classRef.setName(name.replace("'", ""));
        classRef.setInterfaces(new ArrayList<>());
//        classRef.setFields(new HashSet<>());
        return classRef;
    }

    public static ClassReference newInstance(SootClass cls) {
        ClassReference classRef = newInstance(cls.getName());
        classRef.setInterface(cls.isInterface());
        classRef.setAbstract(cls.isAbstract());
        classRef.setHasDefaultConstructor(SemanticUtils.hasDefaultConstructor(cls));
        classRef.setPublic(cls.isPublic());
        classRef.setPhantom(cls.isPhantom());
        classRef.setAnnotations(SemanticUtils.getAnnotations(cls.getTags()));
        classRef.setSerializable(SemanticUtils.isSerializableClass(cls));
        // 提取类属性信息 没用到 剔除
        if (cls.getFieldCount() > 0) {
            for (SootField field : cls.getFields()) {
                classRef.getFields().put(field.getName(), field.getType().toString());
            }
        }
        // 提取父类信息
        if (cls.hasSuperclass()) {
            // 剔除Object类的继承关系，节省继承边数量
            classRef.setHasSuperClass(cls.hasSuperclass());
            classRef.setSuperClass(cls.getSuperclass().getName());
        }
        // 提取接口信息
        if (cls.getInterfaceCount() > 0) {
            classRef.setHasInterfaces(true);
            for (SootClass intface : cls.getInterfaces()) {
                classRef.getInterfaces().add(intface.getName());
            }
        }
        return classRef;
    }

    public void setName(String name) {
        // fix name too long error
        if (name.length() >= 255) {
            this.name = name.substring(0, 254);
        } else {
            this.name = name;
        }
    }

}
