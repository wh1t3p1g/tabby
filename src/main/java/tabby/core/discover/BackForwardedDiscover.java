package tabby.core.discover;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JimpleLocalBox;
import tabby.core.data.Context;
import tabby.core.data.TabbyVariable;
import tabby.core.soot.switcher.Switcher;
import tabby.core.soot.toolkit.PollutedVarsPointsToAnalysis;
import tabby.neo4j.bean.edge.Call;
import tabby.neo4j.bean.ref.MethodReference;
import tabby.neo4j.cache.CacheHelper;
import tabby.neo4j.service.MethodRefService;

import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/12/10
 */
@Slf4j
@Component
public abstract class BackForwardedDiscover implements Discover{

    @Autowired
    public MethodRefService methodRefService;
    @Autowired
    public CacheHelper cacheHelper;

    public List<String> startNodes = new ArrayList<>();
    public Set<String> nextNodes = new HashSet<>();
    public List<Call> calls = new ArrayList<>();

    @Override
    public void run() {
        startNodes = startNodes(); // 获取所有的startNodes
        do{
            for(String startNode:startNodes){
                MethodReference targetMethodRef = cacheHelper.loadMethodRef(startNode);
                if(!constraint(targetMethodRef)){ // 对当前节点检查是否符合条件
                    nextNodes.addAll(spread(targetMethodRef));
                }
            }
            startNodes = new ArrayList<>(nextNodes);
            nextNodes.clear();
        }while (!startNodes.isEmpty());

    }

    @Override
    public List<String> startNodes() {
        return methodRefService.findAllSinks();
    }

    @Override
    public boolean isEndNodes(MethodReference methodRef) {
        return methodRef.isSource();
    }

    @Override
    public boolean check(MethodReference source, MethodReference target) {
        if(!target.isPolluted()) return false;
        Call call = source.findCall(target);
        if(call == null){
            log.debug(source.getSignature() + " call "+target.getSignature() + " unit not found!");
            return false;
        }
        Stmt stmt = (Stmt) call.getUnit();
        InvokeExpr ie = stmt.getInvokeExpr();
        Value baseValue = null;
        List<ValueBox> boxes = stmt.getUseBoxes();
        for(ValueBox box:boxes){
            if(box instanceof JimpleLocalBox){
                baseValue = box.getValue();
                break;
            }
        }

        SootMethod method = source.getCachedMethod();
        Context context = Context.newInstance(method.getSignature());
        context.setHeadMethodContext(true);
        PollutedVarsPointsToAnalysis pta = Switcher.doMethodAnalysis(context, cacheHelper, method, source);
        Map<Local, TabbyVariable> localMap = new HashMap<>();
        if(pta != null){
            localMap = pta.getFlowBefore(call.getUnit());
        }
        boolean flag = true;
        Set<Integer> positions = new HashSet<>();
        for(Integer position:target.getPollutedPosition()){
            if(position == -1){// this
                if(baseValue != null){
                    flag = check(baseValue, localMap, positions);
                }
            }else{
                Value value = ie.getArg(position);
                flag = check(value, localMap, positions);
            }
            if(!flag){
                break;
            }
        }
        context.clear();
        if(flag){
            source.setPolluted(true);
            source.getPollutedPosition().addAll(positions);
        }
        return flag;
    }

    public boolean check(Value value, Map<Local, TabbyVariable> localMap, Set<Integer> positions){
        TabbyVariable var = null;
        String related = null;
        if(value instanceof Local){
            var = localMap.get(value);
        }else if(value instanceof StaticFieldRef){
            var = Context.globalMap.get(value);
        }else if(value instanceof ArrayRef){
            ArrayRef ar = (ArrayRef) value;
            Value baseValue = ar.getBase();
            Value indexValue = ar.getIndex();
            if(baseValue instanceof Local){
                var = localMap.get(baseValue);
                if(indexValue instanceof IntConstant){
                    int index = ((IntConstant) indexValue).value;
                    var = var.getElement(index);
                }
            }
        }else if(value instanceof InstanceFieldRef){
            InstanceFieldRef ifr = (InstanceFieldRef) value;
            SootField sootField = ifr.getField();
            Value base = ifr.getBase();
            if(base instanceof Local){
                var = localMap.get(base);
                var = var.getField(sootField.getName());
            }
        }
        if(var != null && var.isPolluted()){
            related = var.getValue().getRelatedType();
            if(related != null){
                if(related.startsWith("this")){
                    positions.add(-1);
                }else if(related.startsWith("param-")){
                    String[] pos = related.split("\\|");
                    positions.add(Integer.valueOf(pos[0].split("-")[1]));
                }
            }
            return true;
        }
        return false;
    }
}
