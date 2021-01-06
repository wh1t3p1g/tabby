package tabby.core.soot.switcher;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JimpleLocal;
import tabby.core.data.Context;
import tabby.core.data.RulesContainer;
import tabby.core.data.TabbyVariable;
import tabby.core.soot.toolkit.PollutedVarsPointsToAnalysis;
import tabby.neo4j.bean.edge.Call;
import tabby.neo4j.bean.edge.Has;
import tabby.neo4j.bean.ref.ClassReference;
import tabby.neo4j.bean.ref.MethodReference;
import tabby.neo4j.bean.ref.handle.ClassRefHandle;
import tabby.neo4j.cache.CacheHelper;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 *
 */
@Setter
@Getter
@Slf4j
@Component
public class InvokeExprSwitcher extends AbstractJimpleValueSwitch {

    private MethodReference source;
    private Unit unit;
    private PollutedVarsPointsToAnalysis pta;

    private Value baseValue;
    private boolean isPolluted = false;
    private List<Integer> pollutedPosition;

    @Autowired
    private CacheHelper cacheHelper;
    @Autowired
    private RulesContainer rulesContainer;


    @Override
    public void caseStaticInvokeExpr(StaticInvokeExpr v) {
        if(isNecessaryEdge("StaticInvoke", v)){
            SootMethodRef sootMethodRef = v.getMethodRef();
            ClassRefHandle classRefHandle = new ClassRefHandle(sootMethodRef.getDeclaringClass().getName());
            generate(v);
            buildCallRelationship(classRefHandle, sootMethodRef, "StaticInvoke");
        }
    }

    @Override
    public void caseVirtualInvokeExpr(VirtualInvokeExpr v) { // a.A()
        SootMethodRef sootMethodRef = v.getMethodRef();
        baseValue = v.getBase();
        generate(v);
        ClassRefHandle classRefHandle = new ClassRefHandle(v.getBase().getType().toString());
        buildCallRelationship(classRefHandle, sootMethodRef, "VirtualInvoke");
    }

    @Override
    public void caseSpecialInvokeExpr(SpecialInvokeExpr v) {// 初始化 this.xxx()
        SootMethodRef sootMethodRef = v.getMethodRef();
        if(sootMethodRef.getSignature().contains("<init>") && v.getArgCount() == 0) return; // 无参的构造函数 不影响数据流分析
        baseValue = v.getBase();
        generate(v);
        ClassRefHandle classRefHandle = new ClassRefHandle(v.getBase().getType().toString());
        buildCallRelationship(classRefHandle, sootMethodRef, "SpecialInvoke");
    }

    @Override
    public void caseInterfaceInvokeExpr(InterfaceInvokeExpr v) {
        SootMethodRef sootMethodRef = v.getMethodRef();
        baseValue = v.getBase();
        generate(v);
        ClassRefHandle classRefHandle = new ClassRefHandle(v.getBase().getType().toString());
        buildCallRelationship(classRefHandle, sootMethodRef, "InterfaceInvoke");
    }

    public void buildCallRelationship(ClassRefHandle classRefHandle, SootMethodRef sootMethodRef, String invokerType){
        MethodReference target = cacheHelper.loadMethodRef(sootMethodRef);// 递归父类，接口 查找目标函数
        MethodReference source = cacheHelper.loadMethodRef(this.source.getSignature());
        if(target == null){
            // 为了保证target函数的存在，重建methodRef
            // 解决ClassInfoScanner阶段，函数信息收集不完全的问题
            ClassReference classRef = cacheHelper.loadClassRef(sootMethodRef.getDeclaringClass().getName());
            if(classRef == null){// lambda 的情况
                SootClass cls = sootMethodRef.getDeclaringClass();
//                log.debug("Rebuild class " + cls.getName());
                classRef = ClassReference.parse(cls, rulesContainer);
                cacheHelper.add(classRef);
                classRef.getHasEdge().forEach((has) -> {
                    cacheHelper.add(has.getMethodRef());
                });
            }
            target = cacheHelper.loadMethodRef(sootMethodRef);
            if(target == null){
//                log.debug("Rebuild method " + sootMethodRef.getSignature());
                target = MethodReference.parse(classRef.getHandle(), sootMethodRef.resolve());
                Has has = Has.newInstance(classRef, target);
                classRef.getHasEdge().add(has);
                cacheHelper.add(target);
            }
        }

        if(target.isSink()){ // 调用sink函数时，需要符合sink函数的可控点，如果均为可控点，则当前调用是可控的
            for(int i:target.getPollutedPosition()){
                if(pollutedPosition.size() > i+1 && pollutedPosition.get(i+1) == -2){
                    isPolluted = false;
                    break;
                }
            }
        }

        if(source != null && !target.isIgnore() && isPolluted){ // 剔除不可控边
            Call call = Call.newInstance(source, target);
            call.setRealCallType(classRefHandle.getName());
            call.setInvokerType(invokerType);
            call.setPolluted(isPolluted);
            call.setPollutedPosition(new HashSet<>(pollutedPosition));
            call.setUnit(unit);
            call.setLineNum(unit.getJavaSourceStartLineNumber());
            source.getCallEdge().add(call);
            setResult(call);
        }
    }

    public <T> boolean isNecessaryEdge(String type, T v){
        if ("StaticInvoke".equals(type)) { // 对于静态函数调用，只关注 函数参数可控的情况
            StaticInvokeExpr invokeExpr = (StaticInvokeExpr) v;
            if (invokeExpr.getArgCount() == 0) {
                return false;
            }
            List<Value> values = invokeExpr.getArgs();
            for (Value value : values) {
                if (value instanceof JimpleLocal ||
                        ("forName".equals(invokeExpr.getMethodRef().getName()) && value instanceof StringConstant)) { // Class.forName(xxx) 这种情况
                    return true;
                }
            }
        }

        return false;
    }

    public void generate(InvokeExpr ie){
        if(pta == null) return; // 当前不支持指针分析
        pollutedPosition = new LinkedList<>();
        Map<Local, TabbyVariable> localMap = pta.getFlowBefore(unit);

        if(baseValue != null){
            // 预处理
            // 对于基础数据结构作为函数调用者时，我们将忽略其调用边的可控性
            // 为什么？基础数据结构作为调用者时不构成传递链的一部分
            // boolean int long double float short char
            if(baseValue.getType() instanceof PrimType){
                return;
            }else if(baseValue.getType() instanceof ArrayType){
                Type baseType = ((ArrayType) baseValue.getType()).baseType;
                if(baseType instanceof PrimType){
                    return;
                }
            }
        }
        pollutedPosition.add(check(baseValue, localMap));

        for(int i=0; i<ie.getArgCount(); i++){
            pollutedPosition.add(check(ie.getArg(i), localMap));
        }

        for(Integer i:pollutedPosition){
            if (i != -2) {
                isPolluted = true;
                break;
            }
        }
    }

    public int check(Value value, Map<Local, TabbyVariable> localMap){
        if(value == null){
            return -2;
        }
        TabbyVariable var = null;
        String related = null;
        if(value instanceof Local){
            var = localMap.get(value);
        }else if(value instanceof StaticFieldRef){
            var = Context.globalMap.get(value);
        }else if(value instanceof ArrayRef){
            ArrayRef ar = (ArrayRef) value;
            Value baseValue = ar.getBase();
            Value indexValue = ar.getIndex();
            if(baseValue instanceof Local){
                var = localMap.get(baseValue);
                if(indexValue instanceof IntConstant){
                    int index = ((IntConstant) indexValue).value;
                    var = var.getElement(index);
                }
            }
        }else if(value instanceof InstanceFieldRef){
            InstanceFieldRef ifr = (InstanceFieldRef) value;
            SootField sootField = ifr.getField();
            Value base = ifr.getBase();
            if(base instanceof Local){
                var = localMap.get(base);
                var = var.getField(sootField.getName());
            }
        }
        if(var != null && var.isPolluted()){
            related = var.getValue().getRelatedType();
            if(related != null){
                if(related.startsWith("this")){
                    return -1;
                }else if(related.startsWith("param-")){
                    String[] pos = related.split("\\|");
                    return Integer.valueOf(pos[0].split("-")[1]);
                }
            }
        }
        return -2;
    }
}
