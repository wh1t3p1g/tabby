package tabby.neo4j.bean.ref;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.typeconversion.Convert;
import org.neo4j.ogm.typeconversion.UuidStringConverter;
import soot.SootClass;
import tabby.config.GlobalConfiguration;
import tabby.core.data.RulesContainer;
import tabby.neo4j.bean.edge.Extend;
import tabby.neo4j.bean.edge.Has;
import tabby.neo4j.bean.edge.Interfaces;
import tabby.neo4j.bean.ref.handle.ClassRefHandle;

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
    @Convert(UuidStringConverter.class)
    private UUID uuid;

    private String name;

    private String superClass;

    private List<String> interfaces = new ArrayList<>();

    private boolean isInterface = false;
    private boolean hasSuperClass = false;
    private boolean hasInterfaces = false;
    private transient boolean isInitialed = false;
    /**
     * [[name, modifiers, type],...]
     */
    private Set<String> fields = new HashSet<>();

//    private Set<String> annotations = new HashSet<>();

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

    public ClassRefHandle getHandle(){
        return new ClassRefHandle(this.name);
    }

    public static ClassReference parse(SootClass cls, RulesContainer rulesContainer){
        ClassReference classRef = newInstance(cls.getName());
        classRef.setInterface(cls.isInterface());
        // 提取父类信息
        if(cls.hasSuperclass() && !cls.getSuperclass().getName().equals("java.lang.Object")){
            // 剔除Object类的继承关系，节省继承边数量
            classRef.setHasSuperClass(cls.hasSuperclass());
            classRef.setSuperClass(cls.getSuperclass().getName());
        }
        // 提取接口信息
        if(cls.getInterfaceCount() > 0){
            classRef.setHasInterfaces(true);
            cls.getInterfaces().forEach((intface) -> {
                classRef.getInterfaces().add(intface.getName());
            });
        }
        // 提取类属性信息
        if(cls.getFieldCount() > 0){
            cls.getFields().forEach((field) -> {
                List<String> fieldInfo = new ArrayList<>();
                fieldInfo.add(field.getName());
                fieldInfo.add(field.getModifiers()+"");
                fieldInfo.add(field.getType().toString());
                classRef.getFields().add(GlobalConfiguration.GSON.toJson(fieldInfo));
            });
        }
        // 提取类函数信息
        if(cls.getMethodCount() > 0){
            cls.getMethods().forEach((method) -> {
                MethodReference methodRef = MethodReference.parse(classRef.getHandle(), method);
                boolean isSink = rulesContainer.isSink(classRef.getName(), methodRef.getName());
                methodRef.setSink(isSink);

                if(isSink){
                    methodRef.setPolluted(true);
                    methodRef.setRelatedPosition(rulesContainer.getSinkParamPosition(classRef.getName(), methodRef.getName()));
                    methodRef.setInitialed(true);
                    methodRef.setActionInitialed(true);
                }

                methodRef.setIgnore(rulesContainer.isIgnore(classRef.getName(), methodRef.getName()));
                Has has = Has.newInstance(classRef, methodRef);
                classRef.getHasEdge().add(has);
            });
        }

        return classRef;
    }

    public static ClassReference newInstance(String name){
        ClassReference classRef = new ClassReference();
        classRef.setUuid(UUID.randomUUID());
        classRef.setName(name);
        classRef.setInterfaces(new ArrayList<>());
        classRef.setFields(new HashSet<>());
//        classRef.setAnnotations(new HashSet<>());
        return classRef;
    }

    public List<String> toCSV(){
        List<String> ret = new ArrayList<>();
        ret.add(uuid.toString());
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
