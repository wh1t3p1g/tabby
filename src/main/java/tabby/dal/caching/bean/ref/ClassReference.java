package tabby.dal.caching.bean.ref;

import com.google.common.hash.Hashing;
import lombok.Data;
import org.springframework.data.annotation.Transient;
import soot.SootClass;
import tabby.dal.caching.bean.edge.Extend;
import tabby.dal.caching.bean.edge.Has;
import tabby.dal.caching.bean.edge.Interfaces;
import tabby.dal.caching.converter.List2JsonStringConverter;
import tabby.util.SemanticHelper;

import javax.persistence.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private boolean isPhantom = false;
    private boolean isInterface = false;
    private boolean hasSuperClass = false;
    private boolean hasInterfaces = false;
    private boolean hasDefaultConstructor = false;
    private boolean isInitialed = false;
    private boolean isSerializable = false;
    private boolean isStrutsAction = false; // for struts2
    private boolean isAbstract = false;

    /**
     * [[name, modifiers, type],...]
     * 没有用上，删除
     * 超长的字段会导致csv导入neo4j失败
     * 所以直接剔除
     */
//    @Column(columnDefinition = "TEXT")
//    @Convert(converter = Set2JsonStringConverter.class)
//    private Set<String> fields = new HashSet<>();

    @Column(columnDefinition = "TEXT")
    @Convert(converter = List2JsonStringConverter.class)
    private List<String> interfaces = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    @Convert(converter = List2JsonStringConverter.class)
    private List<String> childClassnames = new ArrayList<>();
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
        String id = Hashing.md5() // 相同class生成的id值也相同
                .hashString(name, StandardCharsets.UTF_8)
                .toString();
        classRef.setId(id);
        classRef.setName(name);
        classRef.setInterfaces(new ArrayList<>());
//        classRef.setFields(new HashSet<>());
        return classRef;
    }

    public static ClassReference newInstance(SootClass cls){
        ClassReference classRef = newInstance(cls.getName());
        classRef.setInterface(cls.isInterface());
        classRef.setHasDefaultConstructor(SemanticHelper.hasDefaultConstructor(cls));
        classRef.setAbstract(cls.isAbstract());
        // 提取类属性信息 没用到 剔除
//        if(cls.getFieldCount() > 0){
//            for (SootField field : cls.getFields()) {
//                List<String> fieldInfo = new ArrayList<>();
//                fieldInfo.add(field.getName());
//                fieldInfo.add(Modifier.toString(field.getModifiers()));
//                fieldInfo.add(field.getType().toString());
//                classRef.getFields().add(GlobalConfiguration.GSON.toJson(fieldInfo));
//            }
//        }
        // 提取父类信息
        if(cls.hasSuperclass()){
            classRef.setHasSuperClass(cls.hasSuperclass());
            classRef.setSuperClass(cls.getSuperclass().getName());
        }
        // 提取接口信息
        if(cls.getInterfaceCount() > 0){
            classRef.setHasInterfaces(true);
            for (SootClass intface : cls.getInterfaces()) {
                classRef.getInterfaces().add(intface.getName());
            }
        }
        return classRef;
    }

    public void setName(String name){
        // fix name too long error
        if(name.length() >= 255){
            this.name = name.substring(0, 254);
        }else{
            this.name = name;
        }
    }

}
