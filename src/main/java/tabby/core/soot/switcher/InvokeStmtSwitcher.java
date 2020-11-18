package tabby.core.soot.switcher;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.SootMethodRef;
import soot.Value;
import soot.jimple.*;
import soot.jimple.internal.JimpleLocal;
import tabby.dal.bean.edge.Call;
import tabby.dal.bean.ref.MethodReference;
import tabby.dal.bean.ref.handle.ClassRefHandle;
import tabby.dal.cache.CacheHelper;

import java.util.List;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 *
 */
@Setter
@Getter
@Slf4j
@Component
public class InvokeStmtSwitcher extends AbstractJimpleValueSwitch {

    private MethodReference source;

    @Autowired
    private CacheHelper cacheHelper;

    @Override
    public void caseStaticInvokeExpr(StaticInvokeExpr v) {
        if(isNecessaryEdge("static", v)){
            SootMethodRef sootMethodRef = v.getMethodRef();
            ClassRefHandle classRefHandle = new ClassRefHandle(sootMethodRef.getDeclaringClass().getName());

            buildCallRelationship(classRefHandle, sootMethodRef);
        }
    }

    @Override
    public void caseVirtualInvokeExpr(VirtualInvokeExpr v) {
        SootMethodRef sootMethodRef = v.getMethodRef();
        ClassRefHandle classRefHandle = new ClassRefHandle(v.getBase().getType().toString());
        buildCallRelationship(classRefHandle, sootMethodRef);
    }

    @Override
    public void caseSpecialInvokeExpr(SpecialInvokeExpr v) {// 初始化
        SootMethodRef sootMethodRef = v.getMethodRef();
        ClassRefHandle classRefHandle = new ClassRefHandle(v.getBase().getType().toString());
        buildCallRelationship(classRefHandle, sootMethodRef);
    }

    @Override
    public void caseInterfaceInvokeExpr(InterfaceInvokeExpr v) {
        SootMethodRef sootMethodRef = v.getMethodRef();
        ClassRefHandle classRefHandle = new ClassRefHandle(v.getBase().getType().toString());
        buildCallRelationship(classRefHandle, sootMethodRef);
    }

    @Override
    public void defaultCase(Object v) {
        super.defaultCase(v);
    }

    public void buildCallRelationship(ClassRefHandle classRefHandle, SootMethodRef sootMethodRef){
        MethodReference target = cacheHelper.loadMethodRef(classRefHandle, sootMethodRef.getName(), sootMethodRef.getSignature());
        MethodReference source = cacheHelper.loadMethodRefByHandle(this.source.getHandle());
        if(target != null && source != null){
            Call call = Call.newInstance(source, target);
            call.setRealCallType(classRefHandle.getName());
            source.getCallEdge().add(call);
        }
    }

    public <T> boolean isNecessaryEdge(String type, T v){
        if ("static".equals(type)) { // 对于静态函数调用，只关注 函数参数可控的情况
            StaticInvokeExpr invokeExpr = (StaticInvokeExpr) v;
            if (invokeExpr.getArgCount() == 0) {
                return false;
            }
            List<Value> values = invokeExpr.getArgs();
            for (Value value : values) {
                if (value instanceof JimpleLocal ||
                        value instanceof StringConstant) { // Class.forName(xxx) 这种情况
                    return true;
                }
            }
        }
        return false;
    }
}
