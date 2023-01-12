package tabby.core.collector;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import soot.Modifier;
import soot.SootMethod;
import tabby.core.container.DataContainer;
import tabby.core.data.Context;
import tabby.core.switcher.Switcher;
import tabby.core.toolkit.PollutedVarsPointsToAnalysis;
import tabby.dal.caching.bean.ref.MethodReference;
import tabby.util.TickTock;

/**
 * @author wh1t3P1g
 * @since 2021/4/23
 */
@Slf4j
@Service
@Setter
public class CallGraphCollector {

//    @Async("tabby-collector")
    public void collect(MethodReference methodRef, DataContainer dataContainer, TickTock tickTock){
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

            log.debug(method.getDeclaringClass().getName()+" "+method.getName());

            Context context = Context.newInstance(method.getSignature(), methodRef);

            PollutedVarsPointsToAnalysis pta =
                    Switcher.doMethodAnalysis(
                            context, dataContainer,
                            method, methodRef);
            context.clear();
        }catch (RuntimeException e){
            e.printStackTrace();
        }catch (Exception e){
            if(e instanceof InterruptedException) {
                log.error("Thread interrupted. " + methodRef.getSignature());
            } else {
                log.error("Something error on call graph. "+methodRef.getSignature());
                e.printStackTrace();
            }
        }
        tickTock.countDown();
    }

}
