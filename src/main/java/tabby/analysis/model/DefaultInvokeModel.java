package tabby.analysis.model;

import lombok.Getter;
import lombok.Setter;
import soot.Value;
import soot.ValueBox;
import soot.jimple.*;
import soot.jimple.internal.JimpleLocalBox;
import tabby.core.container.DataContainer;
import tabby.common.bean.edge.Call;
import tabby.common.bean.ref.MethodReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author wh1t3p1g
 * @since 2021/8/31
 */
@Getter
@Setter
public class DefaultInvokeModel{

    private static final List<String> IGNORE_LIST = new ArrayList<>(Arrays.asList(
            "<java.lang.Object: void <init>()>",
            "<java.io.PrintStream: void println(java.lang.String)>",
            "<java.lang.StringBuilder: java.lang.AbstractStringBuilder append(float)>",
            "<java.lang.StringBuilder: java.lang.String toString()>",
            "<java.lang.StringBuilder: int length()>",
            "<java.lang.String: int hashCode()>",
            "<java.lang.String: int length()>",
            "<java.lang.String: boolean equals(java.lang.Object)>"
    ));


    public void apply(Stmt stmt, boolean isManual, MethodReference methodRef,
                         MethodReference targetMethodRef, DataContainer dataContainer) {

        String signature = targetMethodRef.getSignature();
        if(IGNORE_LIST.contains(signature)) return ;
        // 剔除递归调用自身的情况
        if(methodRef.getId().equals(targetMethodRef.getId())) return ;

        InvokeExpr ie = stmt.getInvokeExpr();

        Call call = Call.newInstance(methodRef, targetMethodRef);
        if(isManual){
            call.setRealCallType(targetMethodRef.getClassname());
            call.setInvokerType("ManualInvoke");
        }else{
            call.setRealCallType(getRealCallType(ie, targetMethodRef));
            call.setInvokerType(getInvokeType(ie));
        }
        call.setLineNum(stmt.getJavaSourceStartLineNumber());
        call.generateId();

        if(!methodRef.getCallEdge().contains(call)){
            methodRef.getCallEdge().add(call);
            dataContainer.store(call);
        }
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
