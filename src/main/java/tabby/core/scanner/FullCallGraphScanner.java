package tabby.core.scanner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import soot.Body;
import soot.Modifier;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.JimpleBody;
import soot.jimple.Stmt;
import tabby.core.model.DefaultInvokeModel;
import tabby.dal.caching.bean.ref.MethodReference;
import tabby.config.GlobalConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.*;

/**
 * @author wh1t3p1g
 * @since 2021/12/2
 */
@Slf4j
@Component
public class FullCallGraphScanner extends CallGraphScanner{

    @Override
    public void collect() {
        Collection<MethodReference> targets =
                new ArrayList<>(dataContainer.getSavedMethodRefs().values());
//        log.info("Load necessary method refs.");
//        dataContainer.loadNecessaryMethodRefs();
        log.info("Build call graph. START!");
        total = targets.size();
        split = total / 10;
        split = split==0?1:split;
        int count = 0;
        for (MethodReference target : targets) {
            if(count%split == 0){
                log.info("Status: {}%, Remain: {}", String.format("%.1f",count*0.1/total*1000), (total-count));
            }
            buildCallEdge(target);
            count++;
        }
        log.info("Status: 100%, Remain: 0");
        log.info("Build call graph. DONE!");
    }

    public void buildCallEdge(MethodReference methodRef){
        try{
            SootMethod method = methodRef.getMethod();
            if(method == null) {
                return; // 提取不出内容，不分析
            }

            if(methodRef.isIgnore() || methodRef.isSink()){
                return; // 消除后续的调用边
            }

            if(method.isStatic() && method.getParameterCount() == 0){
                // 静态函数 且 函数入参数量为0 此类函数不影响分析
                methodRef.setInitialed(true);
                return;
            }

            if(method.isAbstract()
                    || Modifier.isNative(method.getModifiers())
                    || method.isPhantom()){
                methodRef.setInitialed(true);
                methodRef.setActionInitialed(true);
                return;
            }

            JimpleBody body = (JimpleBody) retrieveBody(method, method.getSignature());
            DefaultInvokeModel model = new DefaultInvokeModel();
            for(Unit unit:body.getUnits()){
                Stmt stmt = (Stmt) unit;
                if(stmt.containsInvokeExpr()){
                    InvokeExpr ie = stmt.getInvokeExpr();
                    SootMethod targetMethod = ie.getMethod();
                    MethodReference targetMethodRef
                            = dataContainer.getOrAddMethodRef(ie.getMethodRef(), targetMethod);
                    model.apply(stmt, false, methodRef, targetMethodRef, dataContainer);
                }
            }
        }catch (RuntimeException e){
//            log.error(e.getMessage());
            log.error("Something error on call graph. "+methodRef.getSignature());
            log.error(e.getMessage());
//            e.printStackTrace();
        }
    }

    public static Body retrieveBody(SootMethod method, String signature){
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<JimpleBody> future = executor.submit(() -> (JimpleBody) method.retrieveActiveBody());

        JimpleBody body = null;
        try{
            // 为了解决soot获取body不停止的问题，添加线程且最多执行2分钟
            // 超过2分钟可以获取到的body，也可以间接认为是非常大的body，暂不分析
            // 这里两分钟改成配置文件timeout-1，最短1分钟
            body = future.get( Integer.max(GlobalConfiguration.TIMEOUT-1, 1) * 60L, TimeUnit.SECONDS);
        }catch (TimeoutException e){
            throw new RuntimeException("Method Fetch Timeout "+signature);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
        }

        return body;
    }
}
