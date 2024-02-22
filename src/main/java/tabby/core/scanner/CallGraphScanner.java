package tabby.core.scanner;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tabby.config.GlobalConfiguration;
import tabby.core.collector.CallEdgeCollector;
import tabby.core.collector.CallGraphCollector;
import tabby.core.container.DataContainer;
import tabby.common.bean.ref.MethodReference;
import tabby.dal.service.MethodRefService;
import tabby.common.utils.TickTock;

import java.util.*;

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
    public MethodRefService methodRefService;
    @Autowired
    public DataContainer dataContainer;
    @Autowired
    public CallGraphCollector callGraphCollector;
    @Autowired
    private CallEdgeCollector callEdgeCollector;

    public void run() {
        collect();
        save();
    }

    public void collect() {
        if(GlobalConfiguration.IS_FULL_CALL_GRAPH_CONSTRUCT){
            collectAllCallEdge();
        }else{
            List<String> targets = new ArrayList<>(dataContainer.getTargets());;
            log.info("Build call graph. START!");
            log.info("Method Timeout on {} min.", GlobalConfiguration.METHOD_TIMEOUT);
            doCollectWithNewAddedMethods(targets);
            log.info("Build call graph. DONE!");
        }
    }

    public void doCollectWithNewAddedMethods(List<String> targets){
        boolean flag = true;
        while(!targets.isEmpty()){
            doCollect(targets, flag);
            targets = new ArrayList<>(dataContainer.getNewAddedMethodSigs());
            dataContainer.setNewAddedMethodSigs(Collections.synchronizedSet(new HashSet<>()));

            if(targets.size() > 0){
                log.info("Analyse {} newAddedMethods", targets.size());
                flag = false;
            }
        }
    }

    public void doCollect(List<String> targets, boolean show){
        int total = targets.size();
        TickTock tt = new TickTock(total, show);
        Collections.shuffle(targets);
        for(String signature:targets){
            MethodReference ref = dataContainer.getMethodRefBySignature(signature, false);
            if(ref != null){
                callGraphCollector.collect(ref, dataContainer, tt);
            }else{
                tt.countDown();
            }
        }
        tt.awaitUntilCompleted();
    }

    public void collectAllCallEdge(){
        log.info("Build call graph. START!");
        Collection<MethodReference> targets =
                new ArrayList<>(dataContainer.getSavedMethodRefs().values());
        TickTock tickTock = new TickTock(targets.size(), true);
        for (MethodReference target : targets) {
            callEdgeCollector.collect(target, dataContainer, tickTock);
        }
        tickTock.awaitUntilCompleted();
        log.info("Build call graph. DONE!");
    }

    public void save() {
        if(GlobalConfiguration.GLOBAL_FORCE_STOP) return;
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