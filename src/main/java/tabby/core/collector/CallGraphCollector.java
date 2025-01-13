package tabby.core.collector;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import soot.SootMethod;
import tabby.analysis.SimpleTypeAnalysis;
import tabby.analysis.data.Context;
import tabby.analysis.model.*;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.SemanticUtils;
import tabby.common.utils.TickTock;
import tabby.config.GlobalConfiguration;
import tabby.core.container.DataContainer;

import java.util.LinkedList;
import java.util.UUID;

/**
 * @author wh1t3P1g
 * @since 2021/4/23
 */
@Slf4j
@Service
@Setter
public class CallGraphCollector {

    private LinkedList<Model> chains = new LinkedList<>();

    public CallGraphCollector() {
        chains.add(new IgnoreInvokeModel());
        chains.add(new AccessControllerInvokeModel());
        chains.add(new ThreadPoolRunnableInvokeModel());
        chains.add(new ThreadRunnableInvokeModel());
        chains.add(new ProxyInvokeModel());
        chains.add(new DefaultInvokeModel());
    }

    /**
     * 全量调用图生成
     */
    @Async("tabby-collector")
    public void collect(MethodReference methodRef, DataContainer dataContainer, TickTock tickTock) {
        String uuid = UUID.randomUUID().toString();
        try {
            if (methodRef.isIgnore() || methodRef.isInitialed() || methodRef.isBodyParseError()) {
                tickTock.countDown();
                return; // 消除后续的调用边 和 重复分析的情况
            }

            SootMethod method = methodRef.getMethod();
            if (method == null) {
                tickTock.countDown();
                return; // 提取不出内容，不分析
            }

            if (method.isStatic() && method.getParameterCount() == 0 && !GlobalConfiguration.IS_NEED_ANALYSIS_EVERYTHING) {
                // 静态函数 且 函数入参数量为0
                // 此类函数里的函数调用一般均无法可控，就算后续出现了sink函数，我们也没办法利用
                tickTock.countDown();
                return;
            }

            if (method.isAbstract()
                    || method.isNative()
                    || method.isPhantom()) {
                tickTock.countDown();
                return;
            }

            if (methodRef.isDao() || methodRef.isRpc() || methodRef.isMRpc()) {
                // 对于上面3种类型，不进行分析，直接赋予action
                SemanticUtils.applySpecialMethodActions(methodRef);
                tickTock.countDown();
                return;
            }

//            if(!methodRef.getSignature().equals("")){
//                tickTock.countDown();
//                return;
//            }

            log.debug(method.getSignature());
            try (Context context = Context.newInstance(methodRef.getSignature(), methodRef, dataContainer)) {
                dataContainer.getRunningMethods().put(uuid, context);
                context.setInterProcedural(GlobalConfiguration.INTER_PROCEDURAL);
                SimpleTypeAnalysis.processMethod(method, context);
            }
            tickTock.countDown(); // TODO debug
        } catch (RuntimeException e) {
            log.error("Something error on call graph. " + methodRef.getSignature());
            String msg = e.getMessage();
            log.error(msg);
            tickTock.countDown();
        } catch (OutOfMemoryError e) {
            log.error("OOM Error!!!! Force Stop Everything!!!");
            System.exit(1); // just stop
            tickTock.countDown();
            GlobalConfiguration.GLOBAL_FORCE_STOP = true;
        } catch (Exception e) {
            tickTock.countDown();
            if (e instanceof InterruptedException) {
                log.error("Thread interrupted. " + methodRef.getSignature());
                return;
            } else {
                log.error("Something error on call graph. " + methodRef.getSignature());
//                e.printStackTrace();
            }
        } finally {
            dataContainer.getRunningMethods().remove(uuid);
        }

        log.debug("Remain {} methods.", tickTock.getCount());
    }

}
