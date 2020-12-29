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
    private String returnType;
    private Set<String> parameters = new HashSet<>();

    private boolean isSink = false;
    private boolean isSource = false;
    private boolean isStatic = false;
    private boolean isPolluted = false;
    private boolean hasParameters = false;

    /**
     * 污染传递点，主要标记2种类型，this和param
     * 其他函数可以依靠relatedPosition，来判断当前位置是否是通路
     * old=new
     * param-0=other value
     *      param-0=param-1,param-0=this.field
     * return=other value
     *      return=param-0,return=this.field
     * this.field=other value
     * 提示经过当前函数调用后，当前函数参数和返回值的relateType会发生如下变化
     */
    private Map<String, String> actions = new HashMap<>();

    private Set<Integer> pollutedPosition = new HashSet<>();

    private transient ClassRefHandle classRef;
    private transient SootMethod cachedMethod;
    private transient boolean isInitialed = false;
    private transient boolean actionInitialed = false;
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
        csv.add(Boolean.toString(isSource));
        csv.add(Boolean.toString(isPolluted));
        csv.add(String.join("|", parameters));
        csv.add(actions != null ? toStr(actions) : "");
        csv.add(pollutedPosition != null ? toStr(pollutedPosition) : "");
        csv.add(returnType);
        return csv;
    }

    public String toStr(Map<String, String> positions){
        StringBuilder sb = new StringBuilder();
        positions.forEach((position, related)->{
            sb.append(position).append("~").append(related).append(";");
        });
        return sb.toString();
    }

    public String toStr(Set<Integer> positions){
        StringBuilder sb = new StringBuilder();
        positions.forEach((position)->{
            sb.append(position).append("|");
        });
        return sb.toString();
    }

    public Map<String,String> fromStr(String data){
        String[] lines = data.split(";");
        Map<String, String> retData = new HashMap<>();
        for(String line:lines){
            String[] temp = line.split("~");
            retData.put(temp[0], temp[1]);
        }
        return retData;
    }

    @Override
    public int compareTo(MethodReference o) {
        return callEdge.size() - o.getCallEdge().size();
    }

    public Call findCall(MethodReference target){
        for(Call call:callEdge){
            if(call.getTarget().equals(target)){
                return call;
            }
        }
        return null;
    }
}
