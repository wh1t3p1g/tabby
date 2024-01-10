package tabby.analysis.data;

import lombok.Data;
import soot.Local;
import soot.SootField;
import soot.SootFieldRef;
import soot.Value;
import soot.jimple.InstanceFieldRef;
import soot.jimple.StaticFieldRef;
import tabby.common.bean.ref.MethodReference;

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
    private MethodReference methodReference;
    private Map<Local, TabbyVariable> initialMap;
    private Local thisVar;// 设置当前的函数调用时的base变量是什么 或者说是this变量
    private Map<Integer, Local> args = new HashMap<>(); // 前置函数的入参
    private Context preContext;// 如果当前函数为被调用的函数，那么preContext指向之前的函数context
    private int depth; // 当前函数调用深度，限制无限循环的情况
    // 经过flowThough 函数时，拷贝 in集合
    private Map<Local, TabbyVariable> localMap;
    private Map<Local, Set<TabbyVariable>> maybeLocalMap = new HashMap<>();
    private Map<Value, TabbyVariable> globalMap = new HashMap<>();
    // 用于return给当前
    private TabbyVariable returnVar;
    private String topMethodSignature;

    public Context(){
        this.localMap = new HashMap<>();
    }

    public Context(String methodSignature, MethodReference methodReference, Context preContext, int depth) {
        this.methodSignature = methodSignature;
        this.methodReference = methodReference;
        this.topMethodSignature = methodSignature;
        this.depth = depth;
        this.preContext = preContext;
        this.localMap = new HashMap<>();
    }

    public static Context newInstance(String methodSignature, MethodReference methodReference) {
        return new Context(methodSignature, methodReference,null,0);
    }

    /**
     * 创建一个子函数域
     */
    public Context createSubContext(String methodSignature, MethodReference methodReference) {
        Context subContext = new Context(methodSignature, methodReference, this,depth + 1);
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
            SootFieldRef fieldRef = ifr.getFieldRef();

            String signature = null;
            if(sootField != null){
                signature = sootField.getSignature();
            }else if(fieldRef != null){
                signature = fieldRef.getSignature();
            }

            Value base = ifr.getBase();
            if(base instanceof Local){
                TabbyVariable baseVar = getOrAdd(base);
                var = baseVar.getField(signature);
                if(var == null){
                    if(sootField != null){
                        var = baseVar.getOrAddField(baseVar, sootField);
                    }else if(fieldRef != null){
                        var = baseVar.getOrAddField(baseVar, fieldRef);
                    }
                }
                if(var != null){
                    var.setOrigin(ifr);
                }
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
