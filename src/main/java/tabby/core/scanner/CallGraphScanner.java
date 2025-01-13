package tabby.core.scanner;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.TickTock;
import tabby.config.GlobalConfiguration;
import tabby.core.collector.CallGraphCollector;
import tabby.core.container.DataContainer;
import tabby.dal.service.MethodRefService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
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

    public void run() {
        collect();
        save();
    }

    public void collect() {
        List<String> targets;
        if (GlobalConfiguration.IS_ON_DEMAND_DRIVE && GlobalConfiguration.IS_WEB_MODE) { // Web模式下 只分析 各类端点
            log.info("On-Demand-Drive mode, only analysis endpoints.");
            targets = new ArrayList<>(dataContainer.getOnDemandMethods());
        } else {
            targets = new ArrayList<>(dataContainer.getTargets());
        }
        log.info("Build call graph. START!");
        GlobalConfiguration.IS_NEED_ADD_TO_TIMEOUT_LIST = true;
        log.info("Method Timeout on {} min.", GlobalConfiguration.METHOD_TIMEOUT);
        doCollectWithNewAddedMethods(targets);
        int timeoutMethodSize = dataContainer.getAnalyseTimeoutMethodSigs().size();
        if (timeoutMethodSize > 0) {
//            GlobalConfiguration.IS_NEED_ADD_TO_TIMEOUT_LIST = false; // TODO 如果出现大量死循环 还是需要开起来
            GlobalConfiguration.cleanStatus = true;
            GlobalConfiguration.METHOD_TIMEOUT = GlobalConfiguration.METHOD_TIMEOUT * 2; //
            GlobalConfiguration.METHOD_TIMEOUT_SECONDS = GlobalConfiguration.METHOD_TIMEOUT * 60L; //
            log.info("It has {} Methods timeout! Try to Analyse again!", timeoutMethodSize);
            log.info("Method Timeout on {} min.", GlobalConfiguration.METHOD_TIMEOUT);
            targets = new ArrayList<>(dataContainer.getAnalyseTimeoutMethodSigs());
            dataContainer.getAnalyseTimeoutMethodSigs().clear();
            doCollectWithNewAddedMethods(targets);
            GlobalConfiguration.cleanStatus = false;
        }
        timeoutMethodSize = dataContainer.getAnalyseTimeoutMethodSigs().size();
        if (timeoutMethodSize > 0) {
            log.info("It still has {} Methods timeout!", timeoutMethodSize);
        }
        GlobalConfiguration.tickTock = null;
        log.info("Build call graph. DONE!");
    }

    public void doCollectWithNewAddedMethods(List<String> targets) {
        boolean flag = true;
        while (!targets.isEmpty()) {
            doCollect(targets, flag);
            targets = new ArrayList<>(dataContainer.getNewAddedMethodSigs());
            dataContainer.setNewAddedMethodSigs(Collections.synchronizedSet(new HashSet<>()));

            if (targets.size() > 0) {
                log.info("Analyse {} newAddedMethods", targets.size());
                flag = false;
            }
        }
    }

    public void doCollect(List<String> targets, boolean show) {
        int total = targets.size();
        TickTock tt = new TickTock(total, show);
        Collections.shuffle(targets);
        if (GlobalConfiguration.cleanStatus) {
            for (String signature : targets) {
                MethodReference ref = dataContainer.getMethodRefBySignature(signature, false);
                if (ref != null) {
                    ref.cleanStatus();
                }
            }
        }
        for (String signature : targets) {
            MethodReference ref = dataContainer.getMethodRefBySignature(signature, false);
            if (ref != null) {
                collector.collect(ref, dataContainer, tt);
            } else {
                tt.countDown();
            }
        }
        tt.await();
    }

    public void save() {
        if (GlobalConfiguration.GLOBAL_FORCE_STOP) return;
        log.info("Try to save classes.");
        dataContainer.save("class");
        log.info("Try to save methods.");
        dataContainer.save("method");
        log.info("Try to save call edges.");
        dataContainer.save("call");
    }

}