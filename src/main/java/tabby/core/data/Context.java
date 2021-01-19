package tabby.core.data;

import lombok.Data;
import soot.Local;
import soot.SootField;
import soot.Value;
import soot.jimple.InstanceFieldRef;
import soot.jimple.StaticFieldRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
    private Map<Local, TabbyVariable> initialMap;
    private Local thisVar;// 设置当前的函数调用时的base变量是什么 或者说是this变量
    private TabbyVariable baseVar;// 前面调用函数的base变量，比如a.func1() a为base变量
    private Map<Integer, Local> args = new HashMap<>(); // 前置函数的入参
    private Context preContext;// 如果当前函数为被调用的函数，那么preContext指向之前的函数context
    private int depth; // 当前函数调用深度，限制无限循环的情况
    // 经过flowThough 函数时，拷贝 in集合
    private Map<Local, TabbyVariable> localMap;
    private Map<Local, Set<TabbyVariable>> maybeLocalMap = new HashMap<>();
    private Map<Value, TabbyVariable> globalMap = new HashMap<>();
    // return 后需要修改的内容,主要针对入参的修正
    // 比如 param-0:clear 表示当前的函数参数param0的可控状态清楚
    //     param-0:param-1 表示当前的函数参数param0的可控状态需要换成当前的这个relateTypes
    private Map<String, String> returnActions = new HashMap<>();
    // 用于return给当前
    private TabbyVariable returnVar;
    private boolean isHeadMethodContext = false;
    private String topMethodSignature;

    public Context(){
        this.localMap = new HashMap<>();
    }

    public Context(String methodSignature, Context preContext, int depth) {
        this.methodSignature = methodSignature;
        this.topMethodSignature = methodSignature;
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
        Context subContext = new Context(methodSignature, this,depth + 1);
        subContext.setGlobalMap(globalMap); // 同步所有globalmap
        subContext.setTopMethodSignature(topMethodSignature);
        return subContext;
    }


    /**
     * 接受 Local 和 staticField
     * 从localMap 和 globalMap 中分别查询对应的变量实例
     * @param sootValue Local or staticField
     * @return
     */
    public TabbyVariable getOrAdd(Value sootValue) {
        TabbyVariable var = null;
        String valueStr = sootValue.toString();
        if(sootValue instanceof Local){ // find from local map
            var = localMap.get(sootValue);
            if(var == null){ // 新建变量 先从初始表中获取，如果初始表里没有，再新建变量
                TabbyVariable tempVar = initialMap.get(sootValue);
                if(tempVar != null){
                    var = tempVar.deepClone(new ArrayList<>());
                    localMap.put((Local) sootValue, var);
                }
            }
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
        }else if(sootValue instanceof InstanceFieldRef){
            InstanceFieldRef ifr = (InstanceFieldRef) sootValue;
            SootField sootField = ifr.getField();
            Value base = ifr.getBase();
            if(base instanceof Local){
                TabbyVariable baseVar = getOrAdd(base);
                var = baseVar.getField(sootField.getSignature());
                if(var == null){
                    var = baseVar.getOrAddField(baseVar, sootField);
                    var.setOrigin(ifr);
                }
//                    localMap.put((Local) sootValue, var);
            }
        }
        return var;
    }

    public void bindThis(Value value) {
        if(value instanceof Local){
            thisVar = (Local) value;
            TabbyVariable var = getOrAdd(thisVar);
            var.setThis(true);
            var.getValue().setPolluted(true);
            var.getValue().setRelatedType("this");
            var.getFieldMap().forEach((fieldName, fieldVar) -> {
                if(fieldVar != null){
                    fieldVar.getValue().setPolluted(true);
                    fieldVar.getValue().setRelatedType("this|"+fieldName);
                }
            });
        }
    }

    public void bindArg(Local local, int paramIndex) {// 仅用在函数调用处，绑定下一层的变量信息
        TabbyVariable paramVar = getOrAdd(local);
        paramVar.setParam(true);
        paramVar.setParamIndex(paramIndex);
        paramVar.getValue().setPolluted(true);
        paramVar.getValue().setRelatedType("param-"+paramIndex);
        paramVar.getFieldMap().forEach((fieldName, fieldVar) -> {
            if(fieldVar != null){
                fieldVar.getValue().setPolluted(true);
                fieldVar.getValue().setRelatedType("param-"+paramIndex+"|"+fieldName);
            }
        });
        args.put(paramIndex, local);
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
        maybeLocalMap.clear();
    }
}
