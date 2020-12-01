package tabby.core.data;

import lombok.Data;
import soot.Body;
import soot.Local;
import soot.Value;

import java.util.ArrayList;
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
    private List<Value> args; // 当前函数参数
    private TabbyVariable returnVar; // 当前函数返回值

    private TabbyVariable base; // 当出现函数调用a.b()时，存储当前的函数a
    private Context preContext;// 如果当前函数为被调用的函数，那么preMainContext指向之前的函数context
    private int depth; // 当前函数调用深度，限制无限循环的情况
    // 经过flowThough 函数时，拷贝 in集合
    private Map<Local, TabbyVariable> localMap;
    public static Map<Value, TabbyVariable> globalMap;
    // 映射Id与Local的关系,通过Id找到Local，然后通过Local 找当前的localMap里local可能对应的值
    private Map<Integer, Local> queries;
    private TabbyVariable thisVar;

    public Context(){
        this.localMap = new HashMap<>();
    }

    public Context(String methodSignature, List<Value> args, TabbyVariable base, Context preContext,  int depth) {
        this.methodSignature = methodSignature;
        this.args = args == null? new ArrayList<>() : args;
        this.base = base;
        this.depth = depth;
        this.preContext = preContext;
        this.localMap = new HashMap<>();
        this.globalMap = new HashMap<>();
        this.queries = new HashMap<>();
    }

    public static Context newInstance(String methodSignature, Body body) {
        Context context = new Context(methodSignature,  new ArrayList<>(body.getParameterLocals()), null, null,0);
        return context;
    }

    /**
     * 创建一个子函数域
     */
    public Context createSubContext(String methodSignature, List<Value> args, TabbyVariable base) {
        Context subContext = new Context(methodSignature, args, base,this,depth + 1);
        return subContext;
    }



    public TabbyVariable getVariable(Local local) {
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
        if(thisVar == null){
            thisVar = TabbyVariable.newInstance(value);
        }
        thisVar.setPolluted(true); // 默认当前实例是可控的，比如在反序列化中this的field是可以被伪造的
        bindLocalAndVariable((Local) value, thisVar.clone(false, new ArrayList<>()));
    }

    public void bindArg(Local local, int paramIndex) {// 仅用在函数调用处，绑定下一层的变量信息
        if (paramIndex >= 0 && paramIndex < args.size()) {
            Value argValue = args.get(paramIndex);
            if (argValue instanceof Local) {
                TabbyVariable value = preContext == null? null: preContext.getVariable((Local)argValue); // 查找上层函数对应的变量
                TabbyVariable var;
                if (value != null) {
                    var = value.clone(false, new ArrayList<>());
                } else {
                    var = TabbyVariable.newInstance(argValue);
                }
                var.setPolluted(true); // 默认函数参数同样可控
                if(var.getValue() != null){
                    var.getValue().setParam(true);
                    var.getValue().setParamIndex(paramIndex);
                }
                bindLocalAndVariable(local, var);
            }
        }
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
