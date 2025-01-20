package tabby.common.bean.ref;

import com.google.common.base.Objects;
import com.google.common.hash.Hashing;
import jakarta.persistence.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import soot.Body;
import soot.SootClass;
import soot.SootMethod;
import tabby.common.bean.converter.IntArray2JsonStringConverter;
import tabby.common.bean.converter.Map2JsonStringConverter;
import tabby.common.bean.converter.Map2JsonStringForAnnotationsConverter;
import tabby.common.bean.converter.Set2JsonStringConverter;
import tabby.common.bean.edge.Alias;
import tabby.common.bean.edge.Call;
import tabby.common.utils.SemanticUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
    //    @Column(unique = true)
    @Column(columnDefinition = "TEXT")
    private String signature;
    @Column(columnDefinition = "TEXT")
    private String subSignature;
    @Column(columnDefinition = "TEXT")
    private String name0;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = Set2JsonStringConverter.class)
    private Set<String> returnType = new HashSet<>();
    private boolean isReturnConstantType = false;
    private String type;
    private int modifiers;
    private String classname;
    private int parameterSize;
    private String vul;
    private String urlPath;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = Map2JsonStringForAnnotationsConverter.class)
    private Map<String, Map<String, Set<String>>> annotations = new HashMap<>();

    private transient int callCounter = 0;

    private boolean isSink = false;
    private boolean isStatic = false;
    private boolean isPublic = false;
    private boolean hasParameters = false;
    private boolean hasDefaultConstructor = false; // 继承自class
    private boolean isIgnore = false;
    private boolean isSerializable = false;
    private boolean isAbstract = false;
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
    private transient boolean isPreCollected = false;

    private transient boolean isContainSomeError = false;

    /**
     * 污染传递点，主要标记2种类型，this和param
     * 其他函数可以依靠relatedPosition，来判断当前位置是否是通路
     * old=new
     * param-0=other value
     * param-0=param-1,param-0=this.field
     * return=other value
     * return=param-0,return=this.field
     * this.field=other value
     * 提示经过当前函数调用后，当前函数参数和返回值的relateType会发生如下变化
     */
    @Column(columnDefinition = "TEXT")
    @Convert(converter = Map2JsonStringConverter.class)
    private Map<String, Set<String>> actions = Collections.synchronizedMap(new HashMap<>());

    @Convert(converter = IntArray2JsonStringConverter.class)
    private int[][] pollutedPosition;

    private transient Set<Call> callEdge = new HashSet<>();

    private transient Set<Alias> childAliasEdges = new HashSet<>();

    private transient SootMethod sootMethod = null;
    private transient Body body = null;
    private transient AtomicBoolean isRunning = new AtomicBoolean(false);
    private transient AtomicInteger timeoutTimes = new AtomicInteger(0);

    public static MethodReference newInstance(String name, String signature) {
        MethodReference methodRef = new MethodReference();
        String id = null;
        if (signature == null || signature.isEmpty()) {
            id = Hashing.md5()
                    .hashString(UUID.randomUUID().toString(), StandardCharsets.UTF_8)
                    .toString();
        } else {
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

    public static MethodReference newInstance(String classname, SootMethod method) {
        MethodReference methodRef = newInstance(method.getName(), method.getSignature());
        methodRef.setClassname(classname);
        methodRef.setName0(String.format("%s#%s", classname, method.getName()));
        methodRef.setModifiers(method.getModifiers());
        methodRef.setPublic(method.isPublic());
        methodRef.setSubSignature(method.getSubSignature());
        methodRef.setStatic(method.isStatic());
        Set<String> types = new HashSet<>();
        types.add(method.getReturnType().toString());
        methodRef.setReturnType(types);
        methodRef.setReturnConstantType(SemanticUtils.isConstant(method.getReturnType()));
        methodRef.setAbstract(method.isAbstract());
        methodRef.setSootMethod(method);
        if (method.getParameterCount() > 0) {
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
        methodRef.setAnnotations(SemanticUtils.getAnnotations(method.getTags()));

        return methodRef;
    }

    public boolean isEverTimeout() {
        return timeoutTimes.get() >= 3;
    }

    public void incrementTimeoutTimes() {
        timeoutTimes.incrementAndGet();
    }

    public SootMethod getMethod() {
        if (sootMethod != null) return sootMethod;

        SootClass sc = SemanticUtils.getSootClass(classname);
        if (!sc.isPhantom()) {
            sootMethod = SemanticUtils.getMethod(sc, subSignature);
            return sootMethod;
        }

        return null;
    }

    public void setMethod(SootMethod method) {
        if (sootMethod == null) {
            sootMethod = method;
        }
    }

    public void setBody(Body body) {
        if (this.body == null) {
            this.body = body;
        }
    }

    public void setType(String type) {
        this.type = type;
        this.isEndpoint = "web".equals(type);
        this.isNettyEndpoint = "netty".equals(type);
    }

    public boolean isRpc() {
        return "rpc".equals(type);
    }

    public boolean isDao() {
        return "dao".equals(type);
    }

    public boolean isMRpc() {
        return "mrpc".equals(type);
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public void setRunning(boolean flag) {
        isRunning.set(flag);
    }

    public void addCallCounter() {
        callCounter += 1;
    }

    public void cleanStatus() {
        timeoutTimes = new AtomicInteger(0);
        isContainSomeError = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodReference that = (MethodReference) o;
        return isReturnConstantType == that.isReturnConstantType
                && modifiers == that.modifiers
                && parameterSize == that.parameterSize
                && isSink == that.isSink
                && isStatic == that.isStatic
                && isPublic == that.isPublic
                && hasParameters == that.hasParameters
                && hasDefaultConstructor == that.hasDefaultConstructor
                && isSerializable == that.isSerializable
                && isAbstract == that.isAbstract
                && isContainsOutOfMemOptions == that.isContainsOutOfMemOptions
                && isActionContainsSwap == that.isActionContainsSwap
                && isGetter == that.isGetter && isSetter == that.isSetter
                && isFromAbstractClass == that.isFromAbstractClass
                && Objects.equal(id, that.id) && Objects.equal(name, that.name)
                && Objects.equal(signature, that.signature)
                && Objects.equal(subSignature, that.subSignature)
                && Objects.equal(name0, that.name0)
                && Objects.equal(returnType, that.returnType)
                && Objects.equal(type, that.type)
                && Objects.equal(classname, that.classname)
                && Objects.equal(vul, that.vul)
                && Objects.equal(urlPath, that.urlPath)
                && Objects.equal(annotations, that.annotations)
                && Objects.equal(actions, that.actions)
                && Objects.equal(pollutedPosition, that.pollutedPosition);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(
                id, name, signature, subSignature,
                name0, returnType, isReturnConstantType,
                type, modifiers, classname, parameterSize,
                vul, urlPath, annotations, isSink, isStatic, isPublic, hasParameters,
                hasDefaultConstructor, isSerializable, isAbstract, isContainsOutOfMemOptions,
                isActionContainsSwap, isGetter, isSetter, isFromAbstractClass, actions, pollutedPosition);
    }
}
