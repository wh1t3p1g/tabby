package tabby.core.scanner;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import soot.Modifier;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.JimpleBody;
import soot.jimple.Stmt;
import tabby.core.collector.CallGraphCollector;
import tabby.dal.caching.bean.ref.MethodReference;
import tabby.dal.caching.service.MethodRefService;
import tabby.core.data.Context;
import tabby.core.container.DataContainer;
import tabby.core.container.RulesContainer;
import tabby.core.switcher.InvokeExprSwitcher;
import tabby.core.switcher.Switcher;
import tabby.core.toolkit.PollutedVarsPointsToAnalysis;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;

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
    private CallGraphCollector collector;

//    @Resource
//    @Qualifier("multiCallGraphCollector")
//    private Executor executor;

    private static int total;
    private static int split;
    private static int current;

    public void run() {
        collect();
        save();
    }

    public void collect() {
        Collection<MethodReference> targets =
                new ArrayList<>(dataContainer.getSavedMethodRefs().values());
        log.info("Load necessary method refs.");
        dataContainer.loadNecessaryMethodRefs();
        log.info("Build call graph. START!");
        total = targets.size();
        split = total / 10;
        int count = 0;
        for (MethodReference target : targets) {
            if(count%split == 0){
                log.info("Status: {}%, Remain: {}", String.format("%.1f",count*0.1/total*1000), (total-count));
            }
            collector.collect(target, dataContainer);
            count++;
        }
        log.info("Status: 100%, Remain: 0");
        log.info("Build call graph. DONE!");
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