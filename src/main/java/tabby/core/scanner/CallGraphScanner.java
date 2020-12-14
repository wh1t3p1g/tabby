package tabby.core.scanner;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.Modifier;
import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.JimpleBody;
import soot.jimple.Stmt;
import soot.jimple.spark.ondemand.DemandCSPointsTo;
import tabby.core.soot.switcher.InvokeExprSwitcher;
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
        invokeExprSwitcher.setPta((DemandCSPointsTo) Scene.v().getPointsToAnalysis());
        collect(targets);
        // TODO 逆拓扑排序 并分析 这里分析讲简化记录获取的内容
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
            if(methodRef.isSink() || methodRef.isIgnore() || method.isAbstract() || Modifier.isNative(method.getModifiers())){
                methodRef.setInitialed(true);
                methodRef.setPolluted(methodRef.isSink());
                return; // sink点为不动点，无需分析该函数内的调用情况  native/抽象函数没有具体的body
            }
            JimpleBody body = (JimpleBody) method.retrieveActiveBody();
            invokeExprSwitcher.setSource(methodRef);

            for(Unit unit: body.getUnits()){
                Stmt stmt = (Stmt) unit;
                if(stmt.containsInvokeExpr()){
                    invokeExprSwitcher.setUnit(unit);
                    InvokeExpr invokeExpr = stmt.getInvokeExpr();
                    invokeExpr.apply(invokeExprSwitcher);
                }
            }
        }catch (RuntimeException e){
//            e.printStackTrace();
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
