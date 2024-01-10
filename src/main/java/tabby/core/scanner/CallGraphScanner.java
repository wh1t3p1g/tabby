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

import java.util.ArrayList;
import java.util.Collection;

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
        Collection<MethodReference> targets =
                new ArrayList<>(dataContainer.getSavedMethodRefs().values());
        log.info("Build call graph. START!");
        TickTock tickTock = new TickTock(targets.size(), true);
        for (MethodReference target : targets) {
            if(GlobalConfiguration.IS_FULL_CALL_GRAPH_CONSTRUCT){
                callEdgeCollector.collect(target, dataContainer, tickTock);
            }else{
                callGraphCollector.collect(target, dataContainer, tickTock);
            }
        }
        tickTock.await();
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