package tabby.core.collector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import soot.Modifier;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.JimpleBody;
import soot.jimple.Stmt;
import tabby.common.utils.SemanticUtils;
import tabby.core.container.DataContainer;
import tabby.analysis.model.DefaultInvokeModel;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.TickTock;

/**
 * @author wh1t3p1g
 * @since 2023/1/12
 */
@Slf4j
@Service
public class CallEdgeCollector {

    @Async("tabby-collector")
    public void collect(MethodReference methodRef, DataContainer dataContainer, TickTock tickTock){
        try{
            SootMethod method = methodRef.getMethod();
            if(method == null) {
                tickTock.countDown();
                return; // 提取不出内容，不分析
            }

            if(methodRef.isIgnore() || methodRef.isSink()){
                tickTock.countDown();
                return; // 消除后续的调用边
            }

            if(method.isStatic() && method.getParameterCount() == 0){
                // 静态函数 且 函数入参数量为0 此类函数不影响分析
                methodRef.setInitialed(true);
                tickTock.countDown();
                return;
            }

            if(method.isAbstract()
                    || Modifier.isNative(method.getModifiers())
                    || method.isPhantom()){
                methodRef.setInitialed(true);
                methodRef.setActionInitialed(true);
                tickTock.countDown();
                return;
            }

            JimpleBody body = (JimpleBody) SemanticUtils.retrieveBody(method, method.getSignature(), true);
            if(body == null) {
                tickTock.countDown();
                return;
            }

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
