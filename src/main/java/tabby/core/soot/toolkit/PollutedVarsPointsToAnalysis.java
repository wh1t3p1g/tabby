package tabby.core.soot.toolkit;

import lombok.Getter;
import lombok.Setter;
import soot.*;
import soot.jimple.ArrayRef;
import soot.jimple.InstanceFieldRef;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.ForwardFlowAnalysis;
import tabby.core.data.Context;
import tabby.core.data.DataContainer;
import tabby.core.data.TabbyVariable;
import tabby.core.soot.switcher.stmt.SimpleStmtSwitcher;
import tabby.core.soot.switcher.stmt.StmtSwitcher;
import tabby.core.soot.switcher.value.SimpleLeftValueSwitcher;
import tabby.core.soot.switcher.value.SimpleRightValueSwitcher;
import tabby.db.bean.ref.MethodReference;

import java.util.*;

/**
 * 流不敏感指针分析 may alias analysis
 * 主要工作：维护一个当前Unit之前以及之后的变量状态，提供给可控变量分析
 * 工作阶段：位于sinks开始的后向调用分析，前置已存在相应的函数表
 * @author wh1t3P1g
 * @since 2020/11/24
 */
@Setter
@Getter
public class PollutedVarsPointsToAnalysis extends ForwardFlowAnalysis<Unit, Map<Local, TabbyVariable>> {

    private Context context; // 同一函数内共享的上下文内容
    private DataContainer dataContainer;
    private Map<Local, TabbyVariable> emptyMap;
    private Map<Local, TabbyVariable> initialMap;
    private StmtSwitcher stmtSwitcher;
    private MethodReference methodRef;
    private Body body;
    /**
     * Construct the analysis from a DirectedGraph representation of a Body.
     *
     * @param graph
     */
    public PollutedVarsPointsToAnalysis(DirectedGraph<Unit> graph) {
        super(graph);
        emptyMap = new HashMap<>();
        initialMap = new HashMap<>();

    }

    public void doAnalysis(){
        for(ValueBox box:body.getUseAndDefBoxes()){
            Value value = box.getValue();
            Type type = value.getType();
            if(type instanceof PrimType){ // 对于基础数据类型 直接跳过
                continue;
            }
            if(value instanceof Local && !initialMap.containsKey(value)){
                initialMap.put((Local) value, TabbyVariable.makeLocalInstance((Local) value));
            }else if(value instanceof InstanceFieldRef){
                InstanceFieldRef ifr = (InstanceFieldRef) value;
                SootField sootField = ifr.getField();
                Value base = ifr.getBase();
                if(base instanceof Local){
                    TabbyVariable baseVar = initialMap.get(base);
                    if(baseVar == null){
                        baseVar = TabbyVariable.makeLocalInstance((Local) base);
                        initialMap.put((Local) base, baseVar);
                    }
                    TabbyVariable fieldVar = baseVar.getField(sootField.getSignature());
                    if(fieldVar == null){
                        fieldVar = TabbyVariable.makeFieldInstance(baseVar, sootField);
                        baseVar.addField(sootField.getSignature(), fieldVar);
                    }
                }
            }else if(value instanceof ArrayRef){
                ArrayRef v = (ArrayRef) value;
                Value base = v.getBase();
                if(base instanceof Local){
                    TabbyVariable baseVar = initialMap.get(base);
                    if(baseVar == null){
                        baseVar = TabbyVariable.makeLocalInstance((Local) base);
                        initialMap.put((Local) base, baseVar);
                    }
                }
            }
        }
        super.doAnalysis();
    }

    @Override
    protected void flowThrough(Map<Local, TabbyVariable> in, Unit d, Map<Local, TabbyVariable> out) {
        Map<Local, TabbyVariable> newIn = new HashMap<>();
        copy(in, newIn);
        context.setLocalMap(newIn);
        context.setInitialMap(initialMap);
        stmtSwitcher.setContext(context);
        stmtSwitcher.setDataContainer(dataContainer);
        d.apply(stmtSwitcher);
        out.putAll(context.getLocalMap());
        // 考虑以下几种情况： sable thesis 2003 36页
        //      assignment statement p = q;
        //      Identity statement p := @this checked
        //      Allocation statement p = new java.lang.String, p = newarray (int)[12]
        //      field store p.f = q
        //      field load p.f = q
        //      static field store Class.field = q
        //      static field load p = Class.field
        //      array store p[i] = q
        //      array load p = q[i]
        //      cast statement p = (T) q
        //      invocation statement
        //      return statement
        //      throw statement
    }

    @Override
    protected Map<Local, TabbyVariable> newInitialFlow() {
        return new HashMap<>(emptyMap);
    }


    @Override
    protected void merge(Map<Local, TabbyVariable> in1, Map<Local, TabbyVariable> in2, Map<Local, TabbyVariable> out) {
        // TODO 修正
        out.clear();

        in1.forEach((local, var) -> {
            // 筛选出所有的可控变量，剔除不可控变量 因为对后续的分析无任何帮助
            if(var != null && var.isPolluted(-1)){
                out.put(local, var.deepClone(new ArrayList<>()));
            }
        });

        in2.forEach((local, var) -> {
            if(var == null) return;
            boolean polluted = var.isPolluted(-1);
            if(!out.containsKey(local) && polluted){
                out.put(local, var.deepClone(new ArrayList<>()));
            }else if(out.containsKey(local) && polluted){ // 均可控时，选取老的变量
                TabbyVariable temp = out.get(local);
                if(var.equals(temp)) return;
                if(context.getMaybeLocalMap().containsKey(local)){
                    Set<TabbyVariable> vars = context.getMaybeLocalMap().get(local);
                    vars.add(temp.deepClone(new ArrayList<>()));
                    vars.add(var.deepClone(new ArrayList<>()));
                    // 历史上出现过相同的情况，取用第一个变量值
                    out.put(local, (TabbyVariable)vars.toArray()[0]); // 第一个为第一次的in1的支路结果
                }else{
                    Set<TabbyVariable> vars = new HashSet<>();
                    vars.add(temp.deepClone(new ArrayList<>()));
                    vars.add(var.deepClone(new ArrayList<>()));
                    context.getMaybeLocalMap().put(local, vars);
                    // 此时保留in1的结果
                }
            }
            // 如果遇到相同变量，由于当前out集均为可控变量，所以直接采用out集的结果
            // 也就是默认采用in1支路的运行结果
            // 这里会存在一些问题，比如可能因为in2支路的污点情况，丢失后续的相关路径
            // 但tabby做近似处理，暂不考虑这个情况
        });
    }

    @Override
    protected void copy(Map<Local, TabbyVariable> source, Map<Local, TabbyVariable> dest) {
        dest.clear();
        source.forEach((local, variable) -> {
            dest.put(local, variable.deepClone(new ArrayList<>()));
        });
    }

    public static PollutedVarsPointsToAnalysis makeDefault(MethodReference methodRef,
                                                           Body body,
                                                           DirectedGraph<Unit> graph,
                                                           DataContainer dataContainer,
                                                           Context context, boolean reset){
        PollutedVarsPointsToAnalysis analysis = new PollutedVarsPointsToAnalysis(graph);
        // 配置switchers
        StmtSwitcher switcher = new SimpleStmtSwitcher();
        SimpleLeftValueSwitcher leftSwitcher = new SimpleLeftValueSwitcher();
        leftSwitcher.setReset(reset);
        switcher.setReset(reset);
        switcher.setMethodRef(methodRef);
        switcher.setLeftValueSwitcher(leftSwitcher);
        switcher.setRightValueSwitcher(new SimpleRightValueSwitcher());
        // 配置pta依赖
        analysis.setBody(body);
        analysis.setDataContainer(dataContainer);
        analysis.setStmtSwitcher(switcher);
        analysis.setContext(context);
        analysis.setMethodRef(methodRef);
        // 进行分析
        analysis.doAnalysis();
        return analysis;
    }
}
