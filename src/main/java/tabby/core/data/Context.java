package tabby.core.data;

import lombok.Data;
import soot.Local;
import soot.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 函数的域表示
 * 单个函数存在一个主Context，和多个分支Context
 * Context起到域范围的限制，一个主域为函数的起始
 * @author wh1t3P1g
 * @since 2020/11/24
 */
@Data
public class Context {


    private String methodSignature; // 当前函数签名
    private TabbyVariable baseVar;// 设置当前的函数调用时的base变量是什么 或者说是this变量
    private Map<Local, TabbyVariable> args = new HashMap<>();
    private Context preContext;// 如果当前函数为被调用的函数，那么preContext指向之前的函数context
    private int depth; // 当前函数调用深度，限制无限循环的情况
    // 经过flowThough 函数时，拷贝 in集合
    private Map<Local, TabbyVariable> localMap;
    public static Map<Value, TabbyVariable> globalMap = new HashMap<>();
    // return 后需要修改的内容,主要针对入参的修正
    // 比如 param-0:["clear", null] 表示当前的函数参数param0的可控状态清楚
    //     param-0:["replace", [relateTypes]] 表示当前的函数参数param0的可控状态需要换成当前的这个relateTypes
    private Map<String, List<Object>> returnActions = new HashMap<>();
    // 用于return给当前
    private TabbyVariable returnVar;

    public Context(){
        this.localMap = new HashMap<>();
    }

    public Context(String methodSignature, Context preContext, int depth) {
        this.methodSignature = methodSignature;
        this.depth = depth;
        this.preContext = preContext;
        this.localMap = new HashMap<>();
    }

    public static Context newInstance(String methodSignature) {
        return new Context(methodSignature,null,0);
    }

    /**
     * 创建一个子函数域
     */
    public Context createSubContext(String methodSignature) {
        return new Context(methodSignature, this,depth + 1);
    }

    public TabbyVariable getLocalVariable(Local local) {
        return localMap.get(local);
    }

    public TabbyVariable getVariable(Value value){
        if(value instanceof Local){
            return localMap.get((Local) value);
        }else{
            return globalMap.getOrDefault(value, null);
        }
    }

    /**
     * 根据local取出所有可能的variable
     * @param sootValue
     * @return
     */
    public TabbyVariable getOrAdd(Value sootValue) {
        TabbyVariable var;
        if(sootValue instanceof Local){ // find from local map
            var = localMap.get((Local) sootValue);
            if (var == null) {
                var = TabbyVariable.newInstance(sootValue);
                localMap.put((Local)sootValue, var);
            }
        }else{ // find from global map
            var = globalMap.get(sootValue);
            if(var == null){
                var = TabbyVariable.newInstance(sootValue);
                globalMap.put(sootValue, var);
            }
        }
        return var;
    }

    public void bindThis(Value value) {
        if(baseVar == null){
            baseVar = TabbyVariable.newInstance(value);
        }
        baseVar.getValue().setRelatedType("this");
        baseVar.getValue().setThis(true);
        baseVar.getValue().setPolluted(true); // 默认当前实例是可控的，比如在反序列化中this的field是可以被伪造的
        bindLocalAndVariable((Local) value, baseVar);
    }

    public void bindArg(Local local, int paramIndex) {// 仅用在函数调用处，绑定下一层的变量信息
        TabbyVariable paramVar = TabbyVariable.newInstance(local);
        paramVar.getValue().setParam(true);
        paramVar.getValue().setParamIndex(paramIndex);
        paramVar.getValue().setPolluted(true);
        paramVar.getValue().setRelatedType("param-"+paramIndex);
        args.put(local, paramVar);
        bindLocalAndVariable(local, paramVar);
    }

    /**
     * bind是一个重制的过程，修改当前变量的绑定状态
     * 如 a变量本来对应2个可能Variable，但是一旦调用bind，则原有的2个可能变量被取消
     * 不存在 只更新2个可能Variable中的某一个
     * @param local
     * @param var
     */
    public void bindLocalAndVariable(Local local, TabbyVariable var) {
        localMap.put(local, var);
    }

    public void unbind(Value value){
        if(localMap.containsKey(value)){
            localMap.remove(value);
        }else if(globalMap.containsKey(value)){
            globalMap.remove(value);
        }
    }

    /**
     * 防止函数进入递归调用
     * @param invokeSignature
     * @return
     */
    public boolean isInRecursion(String invokeSignature) {
        if (invokeSignature.equals(methodSignature)) {
            return true;
        }
        if (preContext != null) {
            return preContext.isInRecursion(invokeSignature);
        }
        return false;
    }

    public void clear(){
        globalMap.clear();
    }
}
