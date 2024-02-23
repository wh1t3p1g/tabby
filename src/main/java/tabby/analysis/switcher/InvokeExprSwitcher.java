package tabby.analysis.switcher;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JimpleLocal;
import tabby.analysis.PollutedVarsPointsToAnalysis;
import tabby.analysis.data.TabbyVariable;
import tabby.analysis.model.CallEdgeBuilder;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.PositionUtils;
import tabby.core.container.DataContainer;
import tabby.core.container.RulesContainer;

import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 *
 */
@Setter
@Getter
@Slf4j
public class InvokeExprSwitcher extends AbstractJimpleValueSwitch {

    private MethodReference source;
    private Unit unit;
    private PollutedVarsPointsToAnalysis pta;

    private Value baseValue;
    private boolean isPolluted = false;
    private List<Integer> pollutedPosition;
    private Map<Value, TabbyVariable> globalMap = new HashMap<>();
    private Map<Local, TabbyVariable> localMap = new HashMap<>();

    private DataContainer dataContainer;
    private RulesContainer rulesContainer;
    private CallEdgeBuilder builder = new CallEdgeBuilder();


    @Override
    public void caseStaticInvokeExpr(StaticInvokeExpr v) {
//        if(isNecessaryEdge("StaticInvoke", v)){
            generate(v);
            buildCallRelationship(v);
//        }
    }

    @Override
    public void caseVirtualInvokeExpr(VirtualInvokeExpr v) { // a.A()
        baseValue = v.getBase();
        generate(v);
        buildCallRelationship(v);
    }

    @Override
    public void caseSpecialInvokeExpr(SpecialInvokeExpr v) {// 初始化 this.xxx()
        SootMethodRef sootMethodRef = v.getMethodRef();
        if(sootMethodRef.getSignature().contains("<init>") && v.getArgCount() == 0) return; // 无参的构造函数 不影响数据流分析
        baseValue = v.getBase();
        generate(v);
        buildCallRelationship(v);
    }

    @Override
    public void caseInterfaceInvokeExpr(InterfaceInvokeExpr v) {
        baseValue = v.getBase();
        generate(v);
        buildCallRelationship(v);
    }

    public void buildCallRelationship(InvokeExpr ie){
        builder.setPollutedPosition(pollutedPosition);
        builder.build((Stmt) ie, source, dataContainer);
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
            if (i != PositionUtils.NOT_POLLUTED_POSITION) {
                isPolluted = true;
                break;
            }
        }
    }

    public int check(Value value, Map<Local, TabbyVariable> localMap){
        if(value == null){
            return PositionUtils.NOT_POLLUTED_POSITION;
        }
        TabbyVariable var = null;
        if(value instanceof Local){
            var = localMap.get(value);
        }else if(value instanceof StaticFieldRef){
            var = globalMap.get(value);
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
                var = var.getField(sootField.getSignature());
            }
        }
        if(var != null){
            String related = null;
            if(var.isPolluted(PositionUtils.THIS)){ // var本身是pollted的情况
                related = var.getValue().getRelatedType();
            }else if(var.containsPollutedVar(new ArrayList<>())){ // 当前var的类属性，element元素是polluted的情况
                related = var.getFirstPollutedVarRelatedType();
            }
            if(related != null){
                return PositionUtils.getPosition(related);
            }
        }
        return PositionUtils.NOT_POLLUTED_POSITION;
    }
}
