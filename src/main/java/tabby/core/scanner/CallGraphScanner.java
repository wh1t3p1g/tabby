package tabby.core.scanner;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.JimpleBody;
import soot.jimple.Stmt;
import tabby.core.soot.switcher.InvokeStmtSwitcher;
import tabby.dal.bean.ref.MethodReference;
import tabby.dal.cache.CacheHelper;

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
    private InvokeStmtSwitcher invokeStmtSwitcher;

    @Override
    public void run(List<MethodReference> targets) {
        collect(targets);
        build();
    }

    @Override
    public void collect(List<MethodReference> targets) {
        log.info("start to build call graph!");
        targets.forEach(this::collect);
        log.info("build call graph DONE!");
    }

    public void collect(MethodReference methodRef){
        try{
            SootMethod method = methodRef.getCachedMethod();
            JimpleBody body = (JimpleBody) method.getActiveBody();
            invokeStmtSwitcher.setSource(methodRef);
            for(Unit unit: body.getUnits()){
                Stmt stmt = (Stmt) unit;
                if(stmt.containsInvokeExpr()){
                    InvokeExpr invokeExpr = stmt.getInvokeExpr();
                    invokeExpr.apply(invokeStmtSwitcher);
                }
            }
        }catch (RuntimeException e){
//            e.printStackTrace();
        }
    }

    @Override
    public void build() {

    }

    @Override
    public void save() {

    }
}
