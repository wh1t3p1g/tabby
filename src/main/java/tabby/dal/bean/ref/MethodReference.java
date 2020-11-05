package tabby.dal.bean.ref;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.typeconversion.Convert;
import org.neo4j.ogm.typeconversion.UuidStringConverter;
import soot.SootMethod;
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

    private boolean isStatic;

    private transient ClassRefHandle classRef;

    @Relationship(type="CALL", direction = "UNDIRECTED")
    private Set<Call> callEdge = new HashSet<>();

    public MethodRefHandle getHandle() {
        return new MethodRefHandle(classRef, name, signature);
    }

    // 后续添加分析后的数据字段
    public static MethodReference newInstance(String name, String signature){
        MethodReference methodRef = new MethodReference();
        methodRef.setName(name);
        methodRef.setUuid(UUID.randomUUID());
        methodRef.setSignature(signature);
        return methodRef;
    }

    public static MethodReference parse(ClassRefHandle handle, SootMethod method){
        MethodReference methodRef = newInstance(method.getName(), method.getSignature());
        methodRef.setStatic(method.isStatic());
        methodRef.setClassRef(handle);
        return methodRef;
    }

    public List<String> toCSV(){
        List<String> csv = new ArrayList<>();
        csv.add(uuid.toString());
        csv.add(name);
        csv.add(signature);
        csv.add(Boolean.toString(isStatic));
        return csv;
    }
}
