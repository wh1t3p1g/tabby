package tabby.dal.caching.bean.ref;

import com.google.common.hash.Hashing;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import soot.SootClass;
import soot.SootMethod;
import tabby.dal.caching.bean.edge.Alias;
import tabby.dal.caching.bean.edge.Call;
import tabby.dal.caching.converter.ListInteger2JsonStringConverter;
import tabby.dal.caching.converter.Map2JsonStringConverter;
import tabby.dal.caching.converter.Map2JsonStringForAnnotationsConverter;
import tabby.util.SemanticHelper;

import javax.persistence.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author wh1t3P1g
 * @since 2020/10/9
 */
@Data
@Entity
@Slf4j
@Table(name = "methods")
public class MethodReference {

    @Id
    private String id;

    private String name;
    @Column(columnDefinition = "TEXT")
    private String name0;
    //    @Column(unique = true)
    @Column(columnDefinition = "TEXT")
    private String signature;
    @Column(columnDefinition = "TEXT")
    private String subSignature;
    private String returnType;
    private int modifiers;
    private String classname;
    private int parameterSize;
    private String vul;
    private transient int callCounter = 0;

    // 没有用上parameters 删除
//    @Column(columnDefinition = "TEXT")
//    @Convert(converter = Set2JsonStringConverter.class)
//    private Set<String> parameters = new HashSet<>();

    private boolean isSink = false;
    private boolean isSource = false;
    private boolean isStatic = false;
    private boolean isPublic = false;
    private boolean hasParameters = false;
    private boolean hasDefaultConstructor = false; // 继承自class
    private boolean isIgnore = false;
    private boolean isSerializable = false;
    private boolean isAbstract = false;
    private boolean isContainsSource = false;
    private boolean isEndpoint = false;
    private boolean isNettyEndpoint = false;
    private boolean isContainsOutOfMemOptions = false;
    private boolean isActionContainsSwap = false;
    private boolean isGetter = false;
    private boolean isSetter = false;
    private boolean isFromAbstractClass = false;
    private boolean isBodyParseError = false;

    /**
     * 指代当前是否初始化过调用边
     */
    private boolean isInitialed = false;
    /**
     * 指代当前actions是否被初始化过
     * 如果初始化过了，就不需要被覆盖
     */
    private boolean isActionInitialed = false;

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
    @Column(columnDefinition = "TEXT")
    @Convert(converter = Map2JsonStringConverter.class)
    private Map<String, String> actions = new ConcurrentHashMap<>();

    @Convert(converter = ListInteger2JsonStringConverter.class)
    private List<Integer> pollutedPosition = new ArrayList<>();

    @org.springframework.data.annotation.Transient
    private transient Set<Call> callEdge = new HashSet<>();

//    /**
//     * 父类函数、接口函数的依赖边
//     */
//    @org.springframework.data.annotation.Transient
//    private transient Alias aliasEdge;

    @org.springframework.data.annotation.Transient
    private transient Set<Alias> childAliasEdges = new HashSet<>();

    private transient SootMethod sootMethod = null;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = Map2JsonStringForAnnotationsConverter.class)
    private Map<String, Map<String, Set<String>>> annotations = new HashMap<>();

    public static MethodReference newInstance(String name, String signature){
        MethodReference methodRef = new MethodReference();
        String id = null;
        if(signature == null || signature.isEmpty()){
            id = Hashing.md5()
                    .hashString(UUID.randomUUID().toString(), StandardCharsets.UTF_8)
                    .toString();
        }else{
            signature = signature.replace("'", ""); // soot生成的可能会带上'
            id = Hashing.md5() // 相同signature生成的id值也相同
                    .hashString(signature, StandardCharsets.UTF_8)
                    .toString();
        }
        methodRef.setName(name);
        methodRef.setId(id);
        methodRef.setSignature(signature);
        return methodRef;
    }

    public static MethodReference newInstance(String classname, SootMethod method){
        MethodReference methodRef = newInstance(method.getName(), method.getSignature());
        methodRef.setClassname(classname);
        methodRef.setName0(String.format("%s.%s", classname, method.getName()));
        methodRef.setModifiers(method.getModifiers());
        methodRef.setPublic(method.isPublic());
        methodRef.setSubSignature(method.getSubSignature());
        methodRef.setStatic(method.isStatic());
        methodRef.setReturnType(method.getReturnType().toString());
        methodRef.setAbstract(methodRef.isAbstract());
        if(method.getParameterCount() > 0){
            methodRef.setHasParameters(true);
            methodRef.setParameterSize(method.getParameterCount());
            // 移除method param的记录
//            for(int i=0; i<method.getParameterCount();i++){
//                List<Object> param = new ArrayList<>();
//                param.add(i); // param position
//                param.add(method.getParameterType(i).toString()); // param type
//                methodRef.getParameters().add(gson.toJson(param));
//            }
        }
        methodRef.setAnnotations(SemanticHelper.getAnnotations(method.getTags()));
        return methodRef;
    }

    public SootMethod getMethod(){
        if(sootMethod != null) return sootMethod;

        SootClass sc = SemanticHelper.getSootClass(classname);
        if(!sc.isPhantom()){
            sootMethod = SemanticHelper.getMethod(sc, subSignature);
            return sootMethod;
        }

        return null;
    }

    public void setMethod(SootMethod method){
        if(sootMethod == null){
            sootMethod = method;
        }
    }

    public void addAction(String key, String value){
        actions.put(key, value);
    }

    public void addCallCounter(){
        callCounter += 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        MethodReference that = (MethodReference) o;

        return new EqualsBuilder()
                .append(modifiers, that.modifiers)
                .append(parameterSize, that.parameterSize)
                .append(callCounter, that.callCounter)
                .append(isSink, that.isSink).append(isSource, that.isSource)
                .append(isStatic, that.isStatic)
                .append(hasParameters, that.hasParameters).append(isInitialed, that.isInitialed)
                .append(isIgnore, that.isIgnore).append(isSerializable, that.isSerializable)
                .append(isAbstract, that.isAbstract).append(isContainsSource, that.isContainsSource)
                .append(isEndpoint, that.isEndpoint).append(isContainsOutOfMemOptions, that.isContainsOutOfMemOptions)
                .append(isActionContainsSwap, that.isActionContainsSwap).append(id, that.id).append(name, that.name)
                .append(signature, that.signature).append(subSignature, that.subSignature).append(returnType, that.returnType)
                .append(classname, that.classname).append(vul, that.vul).append(isFromAbstractClass, that.isFromAbstractClass)
//                .append(parameters, that.parameters)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(id).append(name).append(signature).append(subSignature).append(returnType)
                .append(modifiers).append(classname).append(parameterSize).append(vul).append(callCounter)
//                .append(parameters)
                .append(isSink).append(isSource).append(isStatic)
                .append(hasParameters).append(isInitialed).append(isIgnore).append(isSerializable)
                .append(isAbstract).append(isContainsSource).append(isEndpoint).append(isContainsOutOfMemOptions)
                .append(isActionContainsSwap).append(isFromAbstractClass).toHashCode();
    }
}
