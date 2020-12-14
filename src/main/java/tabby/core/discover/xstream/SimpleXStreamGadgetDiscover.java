package tabby.core.discover.xstream;

import org.springframework.stereotype.Component;
import soot.*;
import soot.jimple.JimpleBody;
import soot.jimple.spark.ondemand.DemandCSPointsTo;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import tabby.core.data.GadgetChain;
import tabby.core.discover.BackForwardedDiscover;
import tabby.neo4j.bean.edge.Call;
import tabby.neo4j.bean.ref.MethodReference;

import java.util.ArrayList;
import java.util.List;

/**
 * @author wh1t3P1g
 * @since 2020/11/21
 */
@SuppressWarnings({"unchecked"})
@Component
public class SimpleXStreamGadgetDiscover extends BackForwardedDiscover {

    @Override
    public void getSources() {
        // sub signature
        sources = new ArrayList<>();
        sources.add("int hashCode()");
        sources.add("java.lang.String toString()");
        sources.add("int compareTo(java.lang.Object)");
    }

    @Override
    public boolean analysis(String position, GadgetChain gadgetChain, MethodReference source, MethodReference target) {
//        if(position == null){ // 指定当前target method的可控位置，如果为null，则不进行分析
//            return false;
//        }
        boolean flag = false;
//        List<String> positions = Arrays.asList(position.split(","));
        PointsToAnalysis pta = Scene.v().getPointsToAnalysis();
        PointsToAnalysis onDemandAnalysis = DemandCSPointsTo.makeDefault();
        SootMethod method = source.getCachedMethod();
        String targetMethodSignature = target.getSignature();
        if(method == null || targetMethodSignature == null) return false;
        JimpleBody body = (JimpleBody) method.retrieveActiveBody();
        UnitGraph graph = new BriefUnitGraph(body);

//        StmtSwitcher switcher = new SimpleStmtSwitcher();
//        switcher.setLeftValueSwitcher(new SimpleLeftValueSwitcher());
//        switcher.setRightValueSwitcher(new SimpleRightValueSwitcher());
//        if("<java.lang.ProcessBuilder: java.lang.Process start()>".equals(source.getSignature())){
//            System.out.println(1);
//        }
//        VarsPointsToAnalysis analysis = new VarsPointsToAnalysis(graph);
//        analysis.setCacheHelper(cacheHelper);
//        analysis.setStmtSwitcher(switcher);
//
//        Context context = Context.newInstance(source.getSignature(), body);
//        analysis.setContext(context);
//        analysis.doAnalysis();
//        context.clear();

        for(Call call:source.getCallEdge()){
            if(call.getTarget().equals(target)){
                gadgetChain.setInvokerType(call.getInvokerType());
                Unit unit = call.getUnit();
                List<ValueBox> boxes = unit.getUseBoxes();
                for(ValueBox box:boxes){
                    Value value = box.getValue();
                    if(value instanceof Local){
                        PointsToSet reaching_objects = pta.reachingObjects((Local) value);
                        System.out.println(1);
                    }
                }
//                Stmt stmt = (Stmt) unit;
//                Map<Local, TabbyVariable> localMap = analysis.getFlowBefore(unit);
//                Set<String> relatedTypes = new HashSet<>();
//                if(stmt.containsInvokeExpr()){
//                    InvokeExpr ie = stmt.getInvokeExpr();
//                    for(String pos: positions){
//                        Value value = null;
//                        if(pos.contains("-")){
//                            int index = Integer.valueOf(pos.split("-")[1]);
//                            value = ie.getArg(index);
//                        }else{ // this
//                            if(ie instanceof VirtualInvokeExpr){
//                                VirtualInvokeExpr vie = (VirtualInvokeExpr) ie;
//                                value = vie.getBase();
//                            }else if(ie instanceof SpecialInvokeExpr){
//                                SpecialInvokeExpr sie = (SpecialInvokeExpr) ie;
//                                value = sie.getBase();
//                            }else if(ie instanceof InterfaceInvokeExpr){
//                                InterfaceInvokeExpr iie = (InterfaceInvokeExpr) ie;
//                                value = iie.getBase();
//                            }
//                        }
//                        if(value instanceof Local){
//                            TabbyVariable var = localMap.getOrDefault(value, null);
//                            if(var != null && var.isPolluted()){
//                                source.getRelatedPosition().addAll(var.getValue().getRelatedType());
//                                relatedTypes.addAll(var.getValue().getRelatedType());
//                                flag = true;
//                            }
//                        }
//                    }
//                }
//                if(flag && !relatedTypes.isEmpty()){
//                    gadgetChain.setObj(String.join(",", relatedTypes));
//                }
//                break;
            }
        }
        return flag;
    }
}
