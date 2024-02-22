package tabby.core.collector;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import soot.Modifier;
import soot.SootMethod;
import tabby.analysis.data.Context;
import tabby.analysis.switcher.Switcher;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.TickTock;
import tabby.config.GlobalConfiguration;
import tabby.core.container.DataContainer;

import java.util.UUID;

/**
 * @author wh1t3P1g
 * @since 2021/4/23
 */
@Slf4j
@Service
@Setter
public class CallGraphCollector {

    @Async("tabby-collector")
    public void collect(MethodReference methodRef, DataContainer dataContainer, TickTock tickTock){
        String uuid = UUID.randomUUID().toString();
        try{
            SootMethod method = methodRef.getMethod();
            if(method == null) {
                tickTock.countDown();
                return; // 提取不出内容，不分析
            }

            if(method.isPhantom() || methodRef.isSink()
                    || methodRef.isIgnore() || method.isAbstract()
                    || Modifier.isNative(method.getModifiers())){
                methodRef.setInitialed(true);
                tickTock.countDown();
                return; // sink点为不动点，无需分析该函数内的调用情况  native/抽象函数没有具体的body
            }

            if(method.isStatic() && method.getParameterCount() == 0){
                // 静态函数 且 函数入参数量为0 此类函数
                // 对于反序列化来说 均不可控 不进行分析
                methodRef.setInitialed(true);
                tickTock.countDown();
                return;
            }

//            if(!methodRef.getSignature().equals("")){
//                tickTock.countDown();
//                return;
//            }

            log.debug(method.getDeclaringClass().getName()+" "+method.getName()); // TODO debug
            try(Context context = Context.newInstance(method.getSignature(), methodRef)){
                dataContainer.getRunningMethods().put(uuid, context);
                Switcher.doMethodAnalysis(context, dataContainer, method, methodRef);
            }
        }catch (RuntimeException e){
            String msg = e.getMessage();
            if(msg != null && msg.contains("Body retrieve error")){
                log.warn("Body retrieve error: " + methodRef.getSignature());
            }else{
                log.error(msg);
                e.printStackTrace();
            }
        }
        catch (OutOfMemoryError e){
            log.error("OOM Error!!!! Force Stop Everything!!!");
            GlobalConfiguration.GLOBAL_FORCE_STOP = true;
        }
        catch (Exception e){
            if(e instanceof InterruptedException) {
                log.error("Thread interrupted. " + methodRef.getSignature());
            } else {
                log.error("Something error on call graph. "+methodRef.getSignature());
                e.printStackTrace();
            }
        }finally {
            dataContainer.getRunningMethods().remove(uuid);
            methodRef.setRunning(false);
        }
        tickTock.countDown();
    }

}
