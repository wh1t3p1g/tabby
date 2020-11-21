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
        log.info("Start to build call graph!");
        targets.forEach(this::collect);
        log.info("Build call graph DONE!");
    }

    public void collect(MethodReference methodRef){
        try{
            if(methodRef.isSink()) return; // sink点为不动点，无需分析该函数内的调用情况
            SootMethod method = methodRef.getCachedMethod();
            if(method.isAbstract() || Modifier.isNative(method.getModifiers())) return;// native/抽象函数没有具体的body
            JimpleBody body = (JimpleBody) method.retrieveActiveBody();
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
            log.debug(methodRef.getSignature() + " not found");
        }
    }

    @Override
    public void build() {

    }

    @Override
    public void save() {

    }
}
