package tabby.core.scanner;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.Modifier;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.JimpleBody;
import soot.jimple.Stmt;
import tabby.core.data.Context;
import tabby.core.soot.switcher.InvokeExprSwitcher;
import tabby.core.soot.switcher.Switcher;
import tabby.core.soot.toolkit.PollutedVarsPointsToAnalysis;
import tabby.neo4j.bean.ref.MethodReference;
import tabby.neo4j.cache.CacheHelper;

import java.util.List;

/**
 * 收集所有调用关系，这部分不做污点分析
 * @author wh1t3P1g
 * @since 2020/11/17
 */
@Data
@Slf4j
@Component
public class CallGraphScanner implements Scanner<List<MethodReference>>{

    @Autowired
    private CacheHelper cacheHelper;
    @Autowired
    private InvokeExprSwitcher invokeExprSwitcher;

    @Override
    public void run(List<MethodReference> targets) {
        collect(targets);
        build();
    }

    @Override
    public void collect(List<MethodReference> targets) {
        log.info("Start to build call graph!");
        targets.forEach(this::collect);
        log.info("Build call graph DONE!");
    }

    public void collect(MethodReference methodRef){
        try{
            SootMethod method = methodRef.getCachedMethod();

            if(method.isPhantom() || methodRef.isSink()
                    || methodRef.isIgnore() || method.isAbstract()
                    || Modifier.isNative(method.getModifiers())){
                methodRef.setInitialed(true);
                methodRef.setPolluted(methodRef.isSink());
                return; // sink点为不动点，无需分析该函数内的调用情况  native/抽象函数没有具体的body
            }
            //sun.util.resources.LocaleNames: java.lang.Object[][] getContents()
            invokeExprSwitcher.setSource(methodRef);
            log.debug(method.getSignature());

            if(method.isStatic() && method.getParameterCount() == 0){ // 静态函数 且 函数入参数量为0 此类函数 对于反序列化来说 均不可控 不进行分析
                methodRef.setInitialed(true);
                methodRef.setPolluted(methodRef.isSink());
                return;
            }

            Context context = Context.newInstance(method.getSignature());
            context.setHeadMethodContext(true);
            PollutedVarsPointsToAnalysis pta = Switcher.doMethodAnalysis(context, cacheHelper, method, methodRef, true);
            if(pta == null){
                log.error("pat null -> "+method.getSignature());
            }
            invokeExprSwitcher.setPta(pta);

            JimpleBody body = (JimpleBody) method.retrieveActiveBody();
            for(Unit unit: body.getUnits()){
                Stmt stmt = (Stmt) unit;
                if(stmt.containsInvokeExpr()){
                    invokeExprSwitcher.setUnit(unit);
                    invokeExprSwitcher.setBaseValue(null);
                    invokeExprSwitcher.setPolluted(false);
                    InvokeExpr invokeExpr = stmt.getInvokeExpr();
                    invokeExpr.apply(invokeExprSwitcher);
                }
            }
            context.clear();

        }catch (RuntimeException e){
            e.printStackTrace();
//            log.debug(methodRef.getSignature() + " not found");
        }
    }

    @Override
    public void build() {

    }

    @Override
    public void save() {

    }
}
