package tabby.analysis.model;

import lombok.Getter;
import lombok.Setter;
import soot.Value;
import soot.ValueBox;
import soot.jimple.*;
import soot.jimple.internal.JimpleLocalBox;
import tabby.common.bean.edge.Call;
import tabby.common.bean.ref.MethodReference;
import tabby.core.container.DataContainer;

import java.util.ArrayList;
import java.util.List;

/**
 * @author wh1t3p1g
 * @since 2021/8/31
 */
@Getter
@Setter
public class DefaultInvokeModel implements Model{

    private List<Integer> pollutedPosition = new ArrayList<>();

    @Override
    public void setPP(List<Integer> pollutedPosition) {
        this.pollutedPosition = pollutedPosition;
    }

    public boolean apply(Stmt stmt, boolean isManual, MethodReference caller, MethodReference callee, DataContainer dataContainer) {
        if(caller.getId().equals(callee.getId())) return false;

        InvokeExpr ie = stmt.getInvokeExpr();
        Call call = Call.newInstance(caller, callee);
        if(isManual){
            call.setRealCallType(callee.getClassname());
            call.setInvokerType("ManualInvoke");
        }else{
            call.setRealCallType(getRealCallType(ie, callee));
            call.setInvokerType(getInvokeType(ie));
        }

        call.setPollutedPosition(new ArrayList<>(pollutedPosition));
        call.setLineNum(stmt.getJavaSourceStartLineNumber());
        call.generateId();

        if(!caller.getCallEdge().contains(call)){
            caller.getCallEdge().add(call);
            dataContainer.store(call);
        }
        return true;
    }

    public String getInvokeType(InvokeExpr ie){
        String invokeType = "";
        if(ie instanceof StaticInvokeExpr){
            invokeType = "StaticInvoke";
        }else if(ie instanceof VirtualInvokeExpr){
            invokeType = "VirtualInvoke";
        }else if(ie instanceof SpecialInvokeExpr){
            invokeType = "SpecialInvoke";
        }else if(ie instanceof InterfaceInvokeExpr){
            invokeType = "InterfaceInvoke";
        }
        return invokeType;
    }

    public String getRealCallType(InvokeExpr ie, MethodReference targetMethodRef){

        String classname = "";
        List<ValueBox> valueBoxes = ie.getUseBoxes();
        for(ValueBox box:valueBoxes){
            if(box instanceof JimpleLocalBox){
                Value base = box.getValue();
                if(base != null){
                    classname = base.getType().toString();
                }
                break;
            }
        }

        if(classname.isEmpty()){
            classname = targetMethodRef.getClassname();
        }

        return classname;
    }


}
