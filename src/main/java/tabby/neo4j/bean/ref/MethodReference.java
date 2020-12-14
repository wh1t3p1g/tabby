package tabby.neo4j.bean.ref;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.typeconversion.Convert;
import org.neo4j.ogm.typeconversion.UuidStringConverter;
import soot.SootMethod;
import tabby.config.GlobalConfiguration;
import tabby.neo4j.bean.edge.Alias;
import tabby.neo4j.bean.edge.Call;
import tabby.neo4j.bean.ref.handle.ClassRefHandle;
import tabby.neo4j.bean.ref.handle.MethodRefHandle;

import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/10/9
 */
@Getter
@Setter
@NodeEntity(label="Method")
public class MethodReference implements Comparable<MethodReference>{

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

    /**
     * 如果函数体内 存在函数调用 递归分析
     * 暂不采用 逆拓扑 来分析
     */
    private boolean isPolluted = false;
    /**
     * 相对位置，当前函数可能污染的位置/来源
     * 比如有
     * this,param-0,param-1......
     * 则表明，当前函数内部，其返回值/内部逻辑中，存在this，param-0这些位置存在受污染的可能性
     * 比如
     * function A func(A a){
     *     return a;
     * }
     * 则 relatedPosition -> param-0 , 那么在实际调用func的地方，就可以进行判断了
     * 如
     * function void func1(A a){
     *     c = b.func(a)
     *     exec(c)
     * }
     * 此时如果a可控，对应param-0，那么当前func1也是可以污染的
     */
    private Set<String> relatedPosition = new HashSet<>();
    /**
     * 返回值对应的位置/来源
     * 此处主要用作污染传递
     */
    private String returnRelatedPosition;
    private String returnType;

    private transient ClassRefHandle classRef;
    private transient SootMethod cachedMethod;
    private transient Set<MethodReference> cachedAliasMethodRefs = new HashSet<>();
    private transient boolean isInitialed = false;
    private transient boolean isIgnore = false;

    @Relationship(type="CALL")
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
        csv.add(String.join("|", relatedPosition));
        csv.add(returnType);
        return csv;
    }

    @Override
    public int compareTo(MethodReference o) {
        return callEdge.size() - o.getCallEdge().size();
    }
}
