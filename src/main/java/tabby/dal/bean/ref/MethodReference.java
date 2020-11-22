package tabby.dal.bean.ref;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.typeconversion.Convert;
import org.neo4j.ogm.typeconversion.UuidStringConverter;
import soot.SootMethod;
import tabby.config.GlobalConfiguration;
import tabby.dal.bean.edge.Alias;
import tabby.dal.bean.edge.Call;
import tabby.dal.bean.ref.handle.ClassRefHandle;
import tabby.dal.bean.ref.handle.MethodRefHandle;

import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/10/9
 */
@Getter
@Setter
@NodeEntity(label="Method")
public class MethodReference {

    @Id
    @Convert(UuidStringConverter.class)
    private UUID uuid;

    private String name;

    private String signature;
    private String subSignature;

    private boolean isStatic = false;

    private boolean hasParameters = false;

    private boolean isSink = false;

    private Set<String> parameters = new HashSet<>();

    private String returnType;


    private transient ClassRefHandle classRef;
    private transient SootMethod cachedMethod;
    private transient boolean isInitialed = false;
    private transient boolean isIgnore = false;

    @Relationship(type="CALL", direction = "UNDIRECTED")
    private Set<Call> callEdge = new HashSet<>();

    /**
     * 父类函数、接口函数的依赖边
     */
    @Relationship(type="ALIAS", direction = "UNDIRECTED")
    private Alias aliasEdge;

    public MethodRefHandle getHandle() {
        return new MethodRefHandle(classRef, name, signature);
    }

    // TODO 后续添加分析后的数据字段
    public static MethodReference newInstance(String name, String signature){
        MethodReference methodRef = new MethodReference();
        methodRef.setName(name);
        methodRef.setUuid(UUID.randomUUID());
        methodRef.setSignature(signature);
        return methodRef;
    }

    public static MethodReference parse(ClassRefHandle handle, SootMethod method){
        MethodReference methodRef = newInstance(method.getName(), method.getSignature());
        methodRef.setSubSignature(method.getSubSignature());
        methodRef.setStatic(method.isStatic());
        methodRef.setClassRef(handle);
        methodRef.setReturnType(method.getReturnType().toString());
        if(method.getParameterCount() > 0){
            methodRef.setHasParameters(true);
            for(int i=0; i<method.getParameterCount();i++){
                List<Object> param = new ArrayList<>();
                param.add(i); // param position
                param.add(method.getParameterType(i).toString()); // param type
                methodRef.getParameters().add(GlobalConfiguration.GSON.toJson(param));
            }
        }
        methodRef.setCachedMethod(method);
        return methodRef;
    }

    public List<String> toCSV(){
        List<String> csv = new ArrayList<>();
        csv.add(uuid.toString());
        csv.add(name);
        csv.add(signature);
        csv.add(subSignature);
        csv.add(Boolean.toString(isStatic));
        csv.add(Boolean.toString(hasParameters));
        csv.add(Boolean.toString(isSink));
        csv.add(String.join("|", parameters));
        csv.add(returnType);
        return csv;
    }
}
