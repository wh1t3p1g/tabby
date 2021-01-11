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
import tabby.core.data.DataContainer;
import tabby.core.data.RulesContainer;
import tabby.core.soot.switcher.InvokeExprSwitcher;
import tabby.core.soot.switcher.Switcher;
import tabby.core.soot.toolkit.PollutedVarsPointsToAnalysis;
import tabby.db.bean.ref.MethodReference;
import tabby.db.service.MethodRefService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 收集所有调用关系，这部分不做污点分析
 * @author wh1t3P1g
 * @since 2020/11/17
 */
@Data
@Slf4j
@Component
public class CallGraphScanner {

    @Autowired
    private MethodRefService methodRefService;
    @Autowired
    private DataContainer dataContainer;
    @Autowired
    private RulesContainer rulesContainer;

    public void run(Collection<MethodReference> targets) {
        collect(targets);
        save();
    }

    public void collect(Collection<MethodReference> targets) {
        log.info("Build call graph. START!");
        List<MethodReference> clonedTargets = new ArrayList<>(targets);
        clonedTargets.forEach(this::collect);
        log.info("Build call graph. DONE!");
    }

    public void collect(MethodReference methodRef){
        try{
            SootMethod method = methodRef.getMethod();
            if(method == null) return; // 提取不出内容，不分析
            if(method.isPhantom() || methodRef.isSink()
                    || methodRef.isIgnore() || method.isAbstract()
                    || Modifier.isNative(method.getModifiers())){
                methodRef.setInitialed(true);
                methodRef.setPolluted(methodRef.isSink());
                return; // sink点为不动点，无需分析该函数内的调用情况  native/抽象函数没有具体的body
            }
            //sun.util.resources.LocaleNames: java.lang.Object[][] getContents()
            InvokeExprSwitcher invokeExprSwitcher = new InvokeExprSwitcher();
            invokeExprSwitcher.setSource(methodRef);
            invokeExprSwitcher.setDataContainer(dataContainer);
            invokeExprSwitcher.setRulesContainer(rulesContainer);
            log.debug(method.getSignature());

            if(method.isStatic() && method.getParameterCount() == 0){ // 静态函数 且 函数入参数量为0 此类函数 对于反序列化来说 均不可控 不进行分析
                methodRef.setInitialed(true);
                methodRef.setPolluted(methodRef.isSink());
                return;
            }

            Context context = Context.newInstance(method.getSignature());
            context.setHeadMethodContext(true);
            PollutedVarsPointsToAnalysis pta =
                    Switcher.doMethodAnalysis(context, dataContainer, method, methodRef, true);
            if(pta == null){
                log.error("pat null -> "+method.getSignature());
            }
            invokeExprSwitcher.setPta(pta);
            invokeExprSwitcher.setGlobalMap(context.getGlobalMap());
            JimpleBody body = (JimpleBody) method.retrieveActiveBody();
//            methodRef.setBody(body.toString());
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

    public void save() {
        log.info("Save remained data to graphdb. START!");
        dataContainer.save("class");
        dataContainer.save("method");
        dataContainer.save("has");
        dataContainer.save("call");
        dataContainer.save("alias");
        dataContainer.save("extend");
        dataContainer.save("interfaces");
        log.info("Save remained data to graphdb. DONE!");
    }
}
