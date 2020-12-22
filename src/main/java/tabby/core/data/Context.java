package tabby.core.data;

import lombok.Data;
import soot.Local;
import soot.Value;
import soot.jimple.StaticFieldRef;

import java.util.ArrayList;
import java.util.HashMap;
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
    private TabbyVariable thisVar;// 设置当前的函数调用时的base变量是什么 或者说是this变量
    private TabbyVariable baseVar;// 前面调用函数的base变量，比如a.func1() a为base变量
    private Map<Integer, TabbyVariable> args = new HashMap<>(); // 前置函数的入参
    private Map<Integer, TabbyVariable> currentArgs = new HashMap<>(); // 当前新的入参绑定
    private Context preContext;// 如果当前函数为被调用的函数，那么preContext指向之前的函数context
    private int depth; // 当前函数调用深度，限制无限循环的情况
    // 经过flowThough 函数时，拷贝 in集合
    private Map<Local, TabbyVariable> localMap;
    public static Map<Value, TabbyVariable> globalMap = new HashMap<>();
    // return 后需要修改的内容,主要针对入参的修正
    // 比如 param-0:clear 表示当前的函数参数param0的可控状态清楚
    //     param-0:param-1 表示当前的函数参数param0的可控状态需要换成当前的这个relateTypes
    private Map<String, String> returnActions = new HashMap<>();
    // 用于return给当前
    private TabbyVariable returnVar;
    private boolean isHeadMethodContext = false;

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
     * 接受 Local 和 staticField
     * 从localMap 和 globalMap 中分别查询对应的变量实例
     * @param sootValue Local or staticField
     * @return
     */
    public TabbyVariable getOrAdd(Value sootValue) {
        TabbyVariable var = null;
        if(sootValue instanceof Local){ // find from local map
            var = localMap.get(sootValue);
            if (var == null) {
                var = TabbyVariable.makeLocalInstance((Local)sootValue);
                localMap.put((Local) sootValue, var);
            }
        }else if(sootValue instanceof StaticFieldRef){ // find from global map
            var = globalMap.get(sootValue);
            if(var == null){
                var = TabbyVariable.makeStaticFieldInstance((StaticFieldRef) sootValue);
                globalMap.put(sootValue, var);
            }
        }
        return var;
    }

    public void bindThis(Value value) {
        thisVar = TabbyVariable.makeSpecialLocalInstance((Local)value, "this");
        if(baseVar != null){
            thisVar = baseVar.deepClone(new ArrayList<>());
        }
        thisVar.setThis(true);
        thisVar.getValue().setPolluted(true);
        thisVar.getValue().setRelatedType("this");
        bindLocalAndVariable((Local) value, thisVar);
    }

    public void bindArg(Local local, int paramIndex) {// 仅用在函数调用处，绑定下一层的变量信息
        TabbyVariable paramVar = TabbyVariable.makeSpecialLocalInstance(local, "param-"+paramIndex);
        if(args.containsKey(paramIndex) && args.get(paramIndex) != null){ // 跟上一层的变量进行绑定
            paramVar = args.get(paramIndex).deepClone(new ArrayList<>());
        }
        paramVar.setParam(true);
        paramVar.setParamIndex(paramIndex);
        paramVar.getValue().setPolluted(true);
        paramVar.getValue().setRelatedType("param-"+paramIndex);
        currentArgs.put(paramIndex, paramVar);
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
