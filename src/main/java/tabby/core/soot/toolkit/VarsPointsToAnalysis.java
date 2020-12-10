package tabby.core.soot.toolkit;

import lombok.Getter;
import lombok.Setter;
import soot.Local;
import soot.Modifier;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.JimpleBody;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.ForwardFlowAnalysis;
import tabby.core.data.Context;
import tabby.core.data.TabbyVariable;
import tabby.core.soot.switcher.StmtSwitcher;
import tabby.neo4j.bean.ref.MethodReference;
import tabby.neo4j.cache.CacheHelper;

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
public class VarsPointsToAnalysis extends ForwardFlowAnalysis<Unit, Map<Local, TabbyVariable>> {

    private Context context; // 同一函数内共享的上下文内容
    private CacheHelper cacheHelper;
    private Map<Local,TabbyVariable> emptyMap;

    /**
     * Construct the analysis from a DirectedGraph representation of a Body.
     *
     * @param graph
     */
    public VarsPointsToAnalysis(DirectedGraph<Unit> graph) {
        super(graph);
        emptyMap = new HashMap<>();
    }

    public void doAnalysis(){
        super.doAnalysis();
    }

    @Override
    protected void flowThrough(Map<Local,TabbyVariable> in, Unit d, Map<Local,TabbyVariable> out) {
        context.setLocalMap(new HashMap<>(in));
        StmtSwitcher switcher = new StmtSwitcher();
        switcher.setContext(context);
        switcher.setCacheHelper(cacheHelper);
        d.apply(switcher);
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
    protected void merge(Map<Local,TabbyVariable> in1, Map<Local,TabbyVariable> in2, Map<Local,TabbyVariable> out) {
        out.clear();
        out.putAll(in2);
        in1.forEach((local, variable) -> {
            if(variable == null) return;
            if(out.containsKey(local)){
                if(!out.get(local).isPolluted() && variable.isPolluted()){ // 聚合时仅保留可控变量
                    out.put(local, variable.clone(true, new ArrayList<>()));
                }
            }else{
                out.put(local, variable.clone(true, new ArrayList<>()));
            }
        });
    }

    @Override
    protected void copy(Map<Local,TabbyVariable> source, Map<Local,TabbyVariable> dest) {
        dest.clear();
        dest.putAll(source);
    }

    /**
     * 在进入函数调用前，判断是否参数可控，本身对象可控
     * 如 test(a,b); a为可控对象，那么返回[0] 如果a,b均可控，返回[0,1]
     * 如 a.test(b); a为可控对象，那么返回[-1] 如果b也可控，返回[0,1]
     * @param unit
     * @param invokerType
     * @return
     */
    public Set<Integer> mayPolluted(Unit unit, String invokerType){
        Set<Integer> ret = new HashSet<>();
        switch (invokerType){
            case "StaticInvoke":
                // 静态函数调用 查看参数是否可控
                break;
            case "VirtualInvoke":
                // b.test()
                // 查看参数是否可控，调用对象是否可控
                break;
            case "SpecialInvoke":
                // 初始化 this.xxx()
                // this 默认可控
                // 初始化时，如果参数可控，则有可能可控；如果参数不可控，默认不可控
                break;
            case "InterfaceInvoke":
                // 跟virtualInvoke一样
                break;
        }
        return ret;
    }

    public void analysisInvokedMethod(TabbyVariable base, SootMethod method){
        MethodReference methodRef = cacheHelper.loadMethodRef(method.getSignature());
        if(methodRef == null) return; // 只分析当前cache存在的函数
        if(method.isAbstract() || Modifier.isNative(method.getModifiers())) return;
        if(methodRef.isInitialed()) return; // 不重复分析
        try{
            JimpleBody body = (JimpleBody) method.retrieveActiveBody();
            UnitGraph graph = new BriefUnitGraph(body);
            VarsPointsToAnalysis analysis = new VarsPointsToAnalysis(graph);

            Context subContext = context.createSubContext(method.getSignature(), new ArrayList<>(body.getParameterLocals()), base);
            subContext.setThisVar(TabbyVariable.newInstance(body.getThisLocal()));
            analysis.setContext(subContext);
            analysis.setCacheHelper(cacheHelper);
            analysis.doAnalysis();
        }catch (RuntimeException e){
            // do nothing
        }
    }
}
