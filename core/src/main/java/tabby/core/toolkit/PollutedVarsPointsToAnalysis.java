package tabby.core.toolkit;

import lombok.Getter;
import lombok.Setter;
import soot.*;
import soot.jimple.ArrayRef;
import soot.jimple.InstanceFieldRef;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.scalar.ForwardFlowAnalysis;
import tabby.core.data.DataContainer;
import tabby.core.data.TabbyVariable;
import tabby.core.switcher.stmt.SimpleStmtSwitcher;
import tabby.core.switcher.stmt.StmtSwitcher;
import tabby.core.switcher.value.SimpleLeftValueSwitcher;
import tabby.core.switcher.value.SimpleRightValueSwitcher;
import tabby.caching.bean.ref.MethodReference;
import tabby.core.data.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * May alias analysis
 * 主要工作：维护一个当前Unit之前以及之后的变量状态，提供给可控变量分析
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
                SootFieldRef sfr = ifr.getFieldRef();

                String signature = null;
                if(sootField != null){
                    signature = sootField.getSignature();
                }else if(sfr != null){
                    signature = sfr.getSignature();
                }

                Value base = ifr.getBase();
                if(base instanceof Local){
                    TabbyVariable baseVar = initialMap.get(base);
                    if(baseVar == null){
                        baseVar = TabbyVariable.makeLocalInstance((Local) base);
                        initialMap.put((Local) base, baseVar);
                    }
                    TabbyVariable fieldVar = baseVar.getField(signature);
                    if(fieldVar == null){
                        if(sootField != null){
                            fieldVar = TabbyVariable.makeFieldInstance(baseVar, sootField);
                        }else if(sfr != null){
                            fieldVar = TabbyVariable.makeFieldInstance(baseVar, sfr);
                        }
                        if(fieldVar != null && signature != null){
                            fieldVar.setOrigin(value);
                            baseVar.addField(signature, fieldVar);
                        }
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
        out.putAll(clean(context.getLocalMap()));
//        out.putAll(context.getLocalMap());
        // TODO 去掉非污染的变量
        //  影响 加快了分析速度
        //  但是丢失了一部分的关系边（暂未找到这部分缺失的影响，还需要进行实验）
        //  这里暂时为了效率舍弃了部分可控边
    }

    @Override
    protected Map<Local, TabbyVariable> newInitialFlow() {
        return new HashMap<>(emptyMap);
    }

    @Override
    protected void merge(Map<Local, TabbyVariable> in1, Map<Local, TabbyVariable> in2, Map<Local, TabbyVariable> out) {
        // if else while 分支汇聚的时候 对结果集进行处理 取并集
        copy(in1, out);

        in2.forEach((local, in2Var) -> {// 取并集
            TabbyVariable outVar = out.get(local);
            if(outVar != null){
                outVar.union(in2Var);
            }else{
                out.put(local, in2Var);
            }
        });
    }

    @Override
    protected void copy(Map<Local, TabbyVariable> source, Map<Local, TabbyVariable> dest) {
        dest.clear();
        for (Map.Entry<Local, TabbyVariable> entry : source.entrySet()) {
            Local value = entry.getKey();
            TabbyVariable variable = entry.getValue();
            dest.put(value, variable.deepClone(new ArrayList<>()));
        }
    }

    public Map<Local, TabbyVariable> clean(Map<Local, TabbyVariable> localMap){
        // 是否需要剔除不是污染的变量
        Map<Local, TabbyVariable> tmp = new HashMap<>();
        localMap.forEach((local, var) -> {
            if(var.isPolluted(-1)){
                tmp.put(local, var);
            }
        });
        return tmp;
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
