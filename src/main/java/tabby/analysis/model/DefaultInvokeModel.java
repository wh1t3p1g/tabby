package tabby.analysis.model;

import lombok.Getter;
import lombok.Setter;
import soot.Value;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import tabby.analysis.SimpleTypeAnalysis;
import tabby.analysis.container.ValueContainer;
import tabby.analysis.data.SimpleObject;
import tabby.common.bean.edge.Call;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.PositionUtils;
import tabby.common.utils.SemanticUtils;
import tabby.config.GlobalConfiguration;
import tabby.core.container.DataContainer;

import java.util.*;

/**
 * @author wh1t3p1g
 * @since 2021/8/31
 */
@Getter
@Setter
public class DefaultInvokeModel implements Model {

    // from init
    public ValueContainer container;
    public DataContainer dataContainer;
    public MethodReference caller;
    public MethodReference callee;
    // from apply for call
    public List<Set<Integer>> positions;
    public List<Set<String>> types;
    public boolean isCallerThisFieldObj = false;
    public String invokeType = "";
    @Setter
    public int lineNumber = 0;
    public boolean ignore = false;

    @Override
    public void doInit(MethodReference caller, MethodReference callee, ValueContainer container, DataContainer dataContainer) {
        this.caller = caller;
        this.callee = callee;
        this.container = container;
        this.dataContainer = dataContainer;
        positions = new LinkedList<>();
        types = new LinkedList<>();
        isCallerThisFieldObj = false;
        invokeType = "";
        lineNumber = 0;
        ignore = false;
    }

    @Override
    public boolean apply(Stmt stmt) {
        // 剔除递归调用自身的情况
        if (caller.getId().equals(callee.getId())) return true;
        InvokeExpr ie = stmt.getInvokeExpr();
        String type = SemanticUtils.getRealCallType(ie, callee);
        String calleeType = callee.getClassname();
        invokeType = SemanticUtils.getInvokeType(ie);
        lineNumber = stmt.getJavaSourceStartLineNumber();

        if (container != null) {
            // 特殊函数调用纠正
            if (("VirtualInvoke".equals(invokeType) || "InterfaceInvoke".equals(invokeType))
                    && !type.equals(calleeType)) {
                // 对于虚函数调用 和 接口函数调用，尽可能通过类型推断实际的函数调用
                boolean isNeedChange = false;
                if (ie instanceof InstanceInvokeExpr) {
                    Value base = ((InstanceInvokeExpr) ie).getBase();
                    SimpleObject obj = container.getObject(base);
                    Set<Integer> position = container.getPositions(obj, true);
                    position.remove(PositionUtils.NOT_POLLUTED_POSITION);
                    // 需要判断 当前的调用者是可控的，且非this对象（this的field是可以的）
                    // 如果是this对象，则在后续需要将之前的callee替换成新的callee
                    // 如果是不可控对象，则也尝试替换，因为不可控对象的函数调用大概率都是固定的，我们控制不了
                    isNeedChange = position.isEmpty() || container.isThisObj(obj);
                }
                if (isNeedChange) {
                    // 如果获取到的realCallType跟当前调用的method ref不一样，则取type对应的method ref
                    MethodReference realCallee = dataContainer.getOrAddMethodRefBySubSignature(type, callee.getSubSignature());
                    if (realCallee != null && !realCallee.getId().equals(caller.getId())) {
                        callee = realCallee;
                    }
                }
            }
            // 边污点信息抽取
            if (ie instanceof InstanceInvokeExpr) {
                Value base = ((InstanceInvokeExpr) ie).getBase();
                SimpleObject obj = container.getObject(base);
                types.add(container.getObjectTypes(obj));
                positions.add(container.getPositions(obj, true));
                isCallerThisFieldObj = container.isThisObjField(obj);
            } else {
                positions.add(Collections.singleton(PositionUtils.NOT_POLLUTED_POSITION));
                types.add(new HashSet<>());
            }
            // get args obj
            for (Value value : ie.getArgs()) {
                SimpleObject obj = container.getObject(value);
                types.add(container.getObjectTypes(obj));
                positions.add(container.getPositions(obj, true));
            }

            // 优化一些无用的调用边
            // 但同时也可能因为分析不准确的原因导致边的丢失
            if (GlobalConfiguration.IS_NEED_REMOVE_NOT_POLLUTED_CALL_SITE && !callee.isStatic()) {
                // 检查无用的函数调用
                if (SimpleTypeAnalysis.notContainsPollutedValue(ie.getUseBoxes(), container)) {
                    ignore = true;
                    return true; // 当前参数 当前对象都不存在可控变量，直接剔除
                }
                // 检查无用的sink调用 考虑是否去除这部分的检查
//                if(callee.isSink()){
//                    if(!SimpleTypeAnalysis.isValidCall(positions, callee.getPollutedPosition())){ // positions的方式不符合
//                        if(!SimpleTypeAnalysis.isValidCall(ie, callee.getPollutedPosition(), container)){ // ie的方式不符合
//                            return true;
//                        }
//                    }
//                }
            }
        }

        return true;
    }

    @Override
    public void doFinal(Set<Call> callEdges) {
        if(!ignore){
            Call call = Call.newInstance(caller, callee, invokeType, isCallerThisFieldObj, positions, types);
            call.setLineNum(lineNumber);
            callEdges.add(call);
        }
    }
}
