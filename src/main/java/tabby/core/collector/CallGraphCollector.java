package tabby.core.collector;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import soot.Modifier;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.JimpleBody;
import soot.jimple.Stmt;
import tabby.core.container.DataContainer;
import tabby.core.container.RulesContainer;
import tabby.core.data.Context;
import tabby.core.switcher.InvokeExprSwitcher;
import tabby.core.switcher.Switcher;
import tabby.core.toolkit.PollutedVarsPointsToAnalysis;
import tabby.dal.caching.bean.ref.MethodReference;

/**
 * @author wh1t3P1g
 * @since 2021/4/23
 */
@Slf4j
@Service
@Setter
public class CallGraphCollector {

//    @Async("multiCallGraphCollector")
    public void collect(MethodReference methodRef, DataContainer dataContainer){
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

            if(method.isStatic() && method.getParameterCount() == 0){
                // 静态函数 且 函数入参数量为0 此类函数
                // 对于反序列化来说 均不可控 不进行分析
                methodRef.setInitialed(true);
                methodRef.setPolluted(methodRef.isSink());
                return;
            }

//            if(methodRef.isActionInitialed() && methodRef.isInitialed()){
//                // 前面分析时已经分析过了 直接跳过
//                return;
//            }
//            if("<com.runqian.base4.tool.XmlEditor$11: java.awt.Component getTreeCellRendererComponent(javax.swing.JTree,java.lang.Object,boolean,boolean,boolean,int,boolean)>".equals(methodRef.getSignature())){
////            if("<test.FieldSensitivity: void test(benchmark.objects.A)>".equals(methodRef.getSignature())){
//                System.out.println(1);
//            }else{
//                return;
//            }
            log.debug(method.getDeclaringClass().getName()+" "+method.getName());

//            InvokeExprSwitcher invokeExprSwitcher = new InvokeExprSwitcher();
//            invokeExprSwitcher.setSource(methodRef);
//            invokeExprSwitcher.setDataContainer(dataContainer);
//            invokeExprSwitcher.setRulesContainer(dataContainer.getRulesContainer());

            Context context = Context.newInstance(method.getSignature(), methodRef);

            PollutedVarsPointsToAnalysis pta =
                    Switcher.doMethodAnalysis(
                            context, dataContainer,
                            method, methodRef);
            context.clear();
//            if(pta == null){
//                log.error("pat null -> "+method.getSignature()+ ", and skip this method!");
//                return;
//            }
//            invokeExprSwitcher.setPta(pta);
//            invokeExprSwitcher.setGlobalMap(context.getGlobalMap());
//            JimpleBody body = (JimpleBody) method.retrieveActiveBody();
//            for(Unit unit: body.getUnits()){
//                Stmt stmt = (Stmt) unit;
//                if(stmt.containsInvokeExpr()){
//                    invokeExprSwitcher.setUnit(unit);
//                    invokeExprSwitcher.setBaseValue(null);
//                    invokeExprSwitcher.setPolluted(false);
//                    InvokeExpr invokeExpr = stmt.getInvokeExpr();
//                    invokeExpr.apply(invokeExprSwitcher);
//                }
//            }
        }catch (RuntimeException e){
            e.printStackTrace();
//            log.debug(methodRef.getSignature() + " not found");
        }
    }

}
