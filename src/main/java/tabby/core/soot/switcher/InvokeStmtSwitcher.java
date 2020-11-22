package tabby.core.soot.switcher;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.SootClass;
import soot.SootMethodRef;
import soot.Value;
import soot.jimple.*;
import soot.jimple.internal.JimpleLocal;
import tabby.core.data.RulesContainer;
import tabby.dal.bean.edge.Call;
import tabby.dal.bean.edge.Has;
import tabby.dal.bean.ref.ClassReference;
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
    @Autowired
    private RulesContainer rulesContainer;

    @Override
    public void caseStaticInvokeExpr(StaticInvokeExpr v) {
        if(isNecessaryEdge("StaticInvoke", v)){
            SootMethodRef sootMethodRef = v.getMethodRef();
            ClassRefHandle classRefHandle = new ClassRefHandle(sootMethodRef.getDeclaringClass().getName());

            buildCallRelationship(classRefHandle, sootMethodRef, "StaticInvoke");
        }
    }

    @Override
    public void caseVirtualInvokeExpr(VirtualInvokeExpr v) { // a.A()
        SootMethodRef sootMethodRef = v.getMethodRef();
        ClassRefHandle classRefHandle = new ClassRefHandle(v.getBase().getType().toString());
        buildCallRelationship(classRefHandle, sootMethodRef, "VirtualInvoke");
    }

    @Override
    public void caseSpecialInvokeExpr(SpecialInvokeExpr v) {// 初始化 this.xxx()
        SootMethodRef sootMethodRef = v.getMethodRef();
        if(sootMethodRef.getSignature().contains("<init>") && v.getArgCount() == 0) return; // 无参的构造函数 不影响数据流分析
        ClassRefHandle classRefHandle = new ClassRefHandle(v.getBase().getType().toString());
        buildCallRelationship(classRefHandle, sootMethodRef, "SpecialInvoke");
    }

    @Override
    public void caseInterfaceInvokeExpr(InterfaceInvokeExpr v) {
        SootMethodRef sootMethodRef = v.getMethodRef();
        ClassRefHandle classRefHandle = new ClassRefHandle(v.getBase().getType().toString());
        buildCallRelationship(classRefHandle, sootMethodRef, "InterfaceInvoke");
    }

    @Override
    public void defaultCase(Object v) {
        super.defaultCase(v);
    }

    public void buildCallRelationship(ClassRefHandle classRefHandle, SootMethodRef sootMethodRef, String invokerType){
        MethodReference target = cacheHelper.loadMethodRef(sootMethodRef);// 递归父类，接口 查找目标函数
        MethodReference source = cacheHelper.loadMethodRef(this.source.getSignature());
        if(target == null){
            // 为了保证target函数的存在，重建methodRef
            // 解决 ClassInfoScanner阶段，函数信息收集不完全的问题
            ClassReference classRef = cacheHelper.loadClassRef(sootMethodRef.getDeclaringClass().getName());
            if(classRef == null){
                SootClass cls = sootMethodRef.getDeclaringClass();
                log.debug("Rebuild class " + cls.getName());
                classRef = ClassReference.parse(cls, rulesContainer);
                cacheHelper.add(classRef);
                classRef.getHasEdge().forEach((has) -> {
                    cacheHelper.add(has.getMethodRef());
                });
            }
            target = cacheHelper.loadMethodRef(sootMethodRef);
            if(target == null){
                log.debug("Rebuild method " + sootMethodRef.getSignature());
                target = MethodReference.parse(classRef.getHandle(), sootMethodRef.resolve());
                Has has = Has.newInstance(classRef, target);
                classRef.getHasEdge().add(has);
                cacheHelper.add(target);
            }
        }

        if(source != null && !target.isIgnore()){
            Call call = Call.newInstance(source, target);
            call.setRealCallType(classRefHandle.getName());
            call.setInvokerType(invokerType);
            source.getCallEdge().add(call);
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
}
