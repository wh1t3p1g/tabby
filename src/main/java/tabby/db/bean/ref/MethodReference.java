package tabby.db.bean.ref;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.Transient;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import tabby.config.GlobalConfiguration;
import tabby.db.bean.edge.Alias;
import tabby.db.bean.edge.Call;

import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/10/9
 */
@Getter
@Setter
@NodeEntity(label="Method")
public class MethodReference{

    @Id
    private String id;

    private String name;
    private String signature;
    private String subSignature;
    private String returnType;
    private int modifiers;
    private Set<String> parameters = new HashSet<>();
    private String classname;
//    @Transient
//    private String body;

    private boolean isSink = false;
    private boolean isSource = false;
    private boolean isStatic = false;
    private boolean isPolluted = false;
    private boolean hasParameters = false;
    private boolean isInitialed = false;
    private boolean actionInitialed = false;
    private boolean isIgnore = false;
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
    @org.springframework.data.annotation.Transient
    private Map<String, String> realActions = new HashMap<>();
    private String actions;
    private Set<Integer> pollutedPosition = new HashSet<>();

    @Transient
    @org.springframework.data.annotation.Transient
    private SootMethod cachedMethod;

    @org.springframework.data.annotation.Transient
    @Relationship(type="CALL")
    private Set<Call> callEdge = new HashSet<>();

    /**
     * 父类函数、接口函数的依赖边
     */
    @org.springframework.data.annotation.Transient
    @Relationship(type="ALIAS", direction = "UNDIRECTED")
    private Alias aliasEdge;

    // TODO 后续添加分析后的数据字段
    public static MethodReference newInstance(String name, String signature){
        MethodReference methodRef = new MethodReference();
        methodRef.setName(name);
        methodRef.setId(UUID.randomUUID().toString());
        methodRef.setSignature(signature);
        return methodRef;
    }

    public static MethodReference newInstance(String classname, SootMethod method){
        MethodReference methodRef = newInstance(method.getName(), method.getSignature());
        methodRef.setClassname(classname);
        methodRef.setModifiers(method.getModifiers());
        methodRef.setSubSignature(method.getSubSignature());
        methodRef.setStatic(method.isStatic());
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
//        methodRef.setCachedMethod(method);
        return methodRef;
    }

    public void setActions(Map<String, String> actionMap){
        realActions = actionMap;
        actions = GlobalConfiguration.GSON.toJson(actionMap);
    }

    public void addAction(String key, String value){
        realActions.put(key, value);
        actions = GlobalConfiguration.GSON.toJson(realActions);
    }

    public Map<String, String> getActions(){
        if(!realActions.isEmpty()){
            return realActions;
        }else if(actions != null && !"".equals(actions)){
            realActions = GlobalConfiguration.GSON.fromJson(actions, Map.class);
        }
    }

    public List<String> toCSV(){
        List<String> csv = new ArrayList<>();
        csv.add(id);
        csv.add(name);
        csv.add(signature);
        csv.add(subSignature);
        csv.add(modifiers+"");
        csv.add(Boolean.toString(isStatic));
        csv.add(Boolean.toString(hasParameters));
        csv.add(Boolean.toString(isSink));
        csv.add(Boolean.toString(isSource));
        csv.add(Boolean.toString(isPolluted));
        csv.add(String.join("|", parameters));
        csv.add(actions);
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

    public Call findCall(MethodReference target){
        for(Call call:callEdge){
            if(call.getTarget().equals(target)){
                return call;
            }
        }
        return null;
    }

    public SootMethod getCachedMethod(){
        if(cachedMethod != null){
            return cachedMethod;
        }

        try{
            SootClass sc = Scene.v().getSootClass(classname);
            if(!sc.isPhantom()){
                cachedMethod = sc.getMethod(subSignature);
                return cachedMethod;
            }
        }catch (Exception ignored){

        }
        return null;
    }


}
