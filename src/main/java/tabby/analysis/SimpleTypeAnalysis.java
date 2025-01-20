package tabby.analysis;

import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import soot.Body;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.ForwardFlowAnalysis;
import tabby.analysis.container.ValueContainer;
import tabby.analysis.data.Context;
import tabby.analysis.data.SimpleObject;
import tabby.analysis.data.SimpleValue;
import tabby.analysis.helper.SemanticHelper;
import tabby.analysis.model.CallEdgeBuilder;
import tabby.analysis.switcher.StmtSwitcher;
import tabby.analysis.switcher.pta.TStmtSwitcher;
import tabby.common.bean.edge.Call;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.PositionUtils;
import tabby.common.utils.SemanticUtils;
import tabby.config.GlobalConfiguration;

import java.util.*;

/**
 * @author wh1t3p1g
 * @since 2021/11/26
 */
@Setter
@Getter
@Slf4j
public class SimpleTypeAnalysis extends ForwardFlowAnalysis<Unit, ValueContainer> implements AutoCloseable {

    private Context context;
    private SimpleValue retValue = null;
    private StmtSwitcher stmtSwitcher = null;
    private CallEdgeBuilder builder = null;
    private Set<String> initialedCallEdge = new HashSet<>();
    private Map<String, Integer> triggerTimes = new HashMap<>();
    /**
     * 当另一个线程已经分析完了，这里不需要保存当前分析的call site
     * 将 isNormalExit 设置为 false, 将不保存call edges
     */
    private boolean isNormalExit = true;
    private Set<Call> callEdges = new HashSet<>();
    private Map<String, Set<String>> actions = new HashMap<>();

    /**
     * Construct the analysis from a DirectedGraph representation of a Body.
     *
     * @param graph
     */
    public SimpleTypeAnalysis(UnitGraph graph) {
        super(graph);
    }

    @Override
    protected void flowThrough(ValueContainer in, Unit d, ValueContainer out) {
        // 多线程的情况下，先去判断当前method是否已经由其他线程分析完了，如果分析完了就不再继续分析
        if (context.isForceStop() || context.isAnalyseTimeout() ||
                GlobalConfiguration.GLOBAL_FORCE_STOP) {
            // 程序分析因为一些原因导致死循环 但仍然保存call边
            // 或 OOM 直接结束所有分析
            return;
        }

        if (context.getMethodReference().isInitialed()) {
            // 多个线程同时分析一个函数，且当前线程落后其他线程，则直接跳过后续的分析，且不保存当前分析所得的调用边
            isNormalExit = false;
            return;
        }

        if (context.isTimeout()) { // 如果当前函数分析超多最大限时，则停止分析当前函数
            context.setAnalyseTimeout(true);
            isNormalExit = false; // 下一次会重新分析，这里先不保存call边
            return;
        }

//        if(""
//                .equals(context.getMethodSignature())){
//            System.out.println(1);
//        }
//
//        if("".equals(context.getMethodReference().getName0())){
//            System.out.println(1);
//        }
//
//        if("".equals(d.toString())){
//            System.out.println(1);
//        }

        // try to build call edge
        buildCallEdge((Stmt) d, callEdges, in);
        // try to analysis next stmt
        copy(in, out);
        context.accept(out);
        stmtSwitcher.accept(context);
        stmtSwitcher.setTriggerTimes(triggerTimes);
        d.apply(stmtSwitcher); // TODO debug
        // 去除0引用的values 和 fieldObj
//        out.autoClean();

        out.cleanUnnecessaryPts(out.getPointToSet());
//
//        if(GlobalConfiguration.DEBUG_METHOD_SIGNATURE.equals(context.getMethodSignature())){
//            System.out.println(d);
//            diff(in, out);
//        }

    }

    @Override
    protected ValueContainer newInitialFlow() {
        return new ValueContainer(context.getDataContainer());
    }

    @Override
    protected void merge(ValueContainer in1, ValueContainer in2, ValueContainer out) {
        copy(in1, out);
        // out = in1 + in2
        out.union(in2);
    }

    @Override
    protected void copy(ValueContainer source, ValueContainer dest) {
        dest.copy(source);
    }

    public void doAnalysis() {
        stmtSwitcher = new TStmtSwitcher();
        stmtSwitcher.setActions(actions);
        super.doAnalysis();
    }

    public void doEnd() {
        MethodReference ref = context.getMethodReference();
        context.setNormalExit(!context.isForceStop()); // 如果分析过程中出现了死循环，下次不再分析，用simplify处理
        ref.setContainSomeError(context.isForceStop());

        if (isNormalExit || !GlobalConfiguration.IS_NEED_ADD_TO_TIMEOUT_LIST) {
            // 对于到达死循环结束条件的情况 也认为是正常推出
            // 对于timeout的情况，如果是第二次timeout了则直接保存边结果
            context.getDataContainer().store(callEdges, false);
            context.getContainer().cleanUnnecessaryPts(actions);
            SemanticUtils.merge(actions, ref.getActions());
        }

        if (context.isAnalyseTimeout()) {
            if (context.isTopContext()) {
                context.getDataContainer().getAnalyseTimeoutMethodSigs().add(context.getMethodSignature());
            } else {
                Context preContext = context.getPreContext();
                preContext.setAnalyseTimeout(true);
            }
        }

        ref.setRunning(false);

        // 清楚中间的临时数据
        Collection<ValueContainer> containers = unitToAfterFlow.values();
        containers.stream().parallel().forEach(ValueContainer::clear);
        unitToAfterFlow.clear();
        containers = unitToBeforeFlow.values();
        containers.stream().parallel().forEach(ValueContainer::clear);
        unitToBeforeFlow.clear();
    }

    public void buildCallEdge(Stmt stmt, Set<Call> callEdges, ValueContainer container) {
        if ((stmt.containsInvokeExpr()) && !initialedCallEdge.contains(stmt.toString())) {
            builder.build(stmt, context.getMethodReference(), callEdges, container, context.getDataContainer());
            initialedCallEdge.add(stmt.toString());
        }
    }

    public static void makeDefault(
            UnitGraph graph, Context context) {
        MethodReference methodReference = context.getMethodReference();
        // initial
        methodReference.setCallCounter(0);
        // do analysis
        try (SimpleTypeAnalysis analysis = new SimpleTypeAnalysis(graph)) {
            analysis.setContext(context);
            analysis.setBuilder(new CallEdgeBuilder());
            analysis.doAnalysis();
            analysis.doEnd();
            if (!context.isAnalyseTimeout() || !GlobalConfiguration.IS_NEED_ADD_TO_TIMEOUT_LIST) {
                // check actions swap
                Map<String, Set<String>> actions = ImmutableMap.copyOf(methodReference.getActions());
                for (String key : actions.keySet()) {
                    Set<String> action = actions.get(key);
                    if (action == null) continue;
                    boolean flag = false;
                    for (String act : action) {
                        Set<String> action1 = actions.get(act);
                        if (action1 != null && action1.contains(key)) {
                            methodReference.setActionContainsSwap(true);
                            flag = true;
                            break;
                        } else {
                            Set<String> action2 = actions.get(act + "<s>");
                            if (action2 != null && action2.contains(key)) {
                                methodReference.setActionContainsSwap(true);
                                flag = true;
                                break;
                            }
                        }
                    }
                    if (flag) break;
                }
                // check OOM
                methodReference.setContainsOutOfMemOptions(context.isContainsOutOfMemOptions());
                methodReference.setInitialed(true);
                methodReference.setActionInitialed(true);
            }
        }
    }

    /**
     * 返回return value
     *
     * @param method
     * @param context
     * @return
     */
    public static boolean processMethod(SootMethod method, Context context) {
        String signature = "";
        MethodReference methodReference = context.getMethodReference();
        if (methodReference.isBodyParseError()) return false;

        int maxSleepTimes = 5;
        while (methodReference.isRunning() && maxSleepTimes > 0) {
            // 如果已经有线程在运行了，随机睡几秒
            int random = (int) (Math.random() * 20);
            try {
                Thread.sleep(random);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            // 如果睡完，已经分析完了，则直接返回
            if (methodReference.isActionInitialed()) {
                return true;
            }
            maxSleepTimes--;
        }

        try {
            signature = methodReference.getSignature();
            Body body = SemanticUtils.retrieveBody(method, signature, true);

            if (body != null
                    && body.getUnits().getModificationCount() >= GlobalConfiguration.METHOD_MAX_BODY_COUNT) {
                // 超级长的函数 不分析，可能会发生内存泄露的问题
                log.debug("Method {} body is too big, ignore", signature);
                methodReference.setInitialed(true);
                methodReference.setActionInitialed(true);
                return false;
            }

            if (body != null) {
                methodReference.setRunning(true);
                UnitGraph graph = new BriefUnitGraph(body);
                SimpleTypeAnalysis.makeDefault(graph, context);
                return true;
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Body retrieve error: ")) {
                methodReference.setBodyParseError(true);
                log.error(msg);
            } else {
                log.error(msg);
                e.printStackTrace();
            }
        } finally {
            methodReference.setRunning(false);
        }

        return false;
    }


    public static boolean notContainsPollutedValue(List<ValueBox> valueBoxes, ValueContainer in) {
        return !in.containsPollutedValue(valueBoxes);
    }

//    public static List<Set<Integer>> getPositions(InvokeExpr ie, ValueContainer in){
//        List<Set<Integer>> positions = new LinkedList<>();
//        // get base obj
//        if(ie instanceof InstanceInvokeExpr){
//            Value base = ((InstanceInvokeExpr) ie).getBase();
//            String name = SemanticHelper.getSimpleName0(base);
//            positions.add(in.getPositions(name));
//        }else{
//            positions.add(Collections.singleton(PositionHelper.NOT_POLLUTED_POSITION));
//        }
//        // get args obj
//        for(Value value:ie.getArgs()){
//            String name = SemanticHelper.getSimpleName0(value);
//            positions.add(in.getPositions(name));
//        }
//        return positions;
//    }

    /**
     * 检查当前call边上的污点信息是否符合method的污点要求
     *
     * @param callPollutedPositions
     * @param methodPollutedPositions
     * @return
     */
    public static boolean isValidCall(List<Set<Integer>> callPollutedPositions, List<Integer> methodPollutedPositions) {
        if (methodPollutedPositions == null) return false;

        // 剔除无效的pos，一般不会有这些
        methodPollutedPositions.removeIf(o -> o == PositionUtils.NOT_POLLUTED_POSITION);

        // methodPollutedPositions 仅可能存在 -1，0-n
        int length = callPollutedPositions.size();
        for (int pos : methodPollutedPositions) {
            int index = pos + 1;
            if (index < length && index >= 0) {
                Set<Integer> positions = callPollutedPositions.get(index);
                if (positions != null) {
                    Set<Integer> copied = new HashSet<>(positions);
                    copied.remove(PositionUtils.NOT_POLLUTED_POSITION);
                    if (copied.isEmpty()) {
                        return false;
                    }
                }
            } else if (pos == PositionUtils.SOURCE) {
                // do nothing
            } else {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidCall(InvokeExpr ie, List<Integer> methodPollutedPositions, ValueContainer container) {
        SimpleObject[] objects = SemanticHelper.extractObjectFromInvokeExpr(ie, container);

        // 剔除无效的pos，一般不会有这些
        methodPollutedPositions.removeIf(o -> o == PositionUtils.NOT_POLLUTED_POSITION);
        int length = objects.length;

        for (int pos : methodPollutedPositions) {
            int index = pos + 1;
            if (index < length && index >= 0) {
                if (!container.isPolluted(objects[index], new HashSet<>())) return false;
            } else if (pos == PositionUtils.SOURCE) {
                // do nothing
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * 测试debug用
     *
     * @param o1
     * @param o2
     */
    public static void diff(ValueContainer o1, ValueContainer o2) {
        if (!o1.getPointToSet().equals(o2.getPointToSet())) {
            Map<String, Set<String>> o2Pts = new HashMap<>(o2.getPointToSet());
            o1.getPointToSet().forEach((key, values1) -> {
                Set<String> values2 = o2.getPointToSet().get(key);
                if (values2 != null) {
                    o2Pts.remove(key);
                }
                if (!values1.equals(values2)) {
                    System.out.println(key + ", \nold: " + values1 + ", \nnew: " + values2);
                }
            });
            o2Pts.forEach((key, value) -> {
                System.out.println(key + ", null, " + value);
            });
        }
        System.out.println("objects");
        if (!o1.getLocalObjects().equals(o2.getLocalObjects())) {
            Map<String, SimpleObject> o2LocalObjects = new HashMap<>(o2.getLocalObjects());
            o1.getLocalObjects().forEach((key, values1) -> {
                SimpleObject values2 = o2.getLocalObjects().get(key);
                if (values2 != null) o2LocalObjects.remove(key);
                if (!values1.equals(values2)) {
                    System.out.println(key + ", \nold: " + values1 + ", \nnew: " + values2);
                }
            });
            o2LocalObjects.forEach((key, value) -> {
                System.out.println(key + ", null, " + value);
            });
        }
        System.out.println("value");
        if (!o1.getLocalValues().equals(o2.getLocalValues())) {
            Map<String, SimpleValue> o2LocalValues = new HashMap<>(o2.getLocalValues());
            o1.getLocalValues().forEach((key, values1) -> {
                SimpleValue values2 = o2.getLocalValues().get(key);
                if (values2 != null) o2LocalValues.remove(key);
                if (!values1.equals(values2)) {
                    System.out.println(key + ", \nold: " + values1 + ", \nnew:" + values2);
                }
            });
            o2LocalValues.forEach((key, value) -> {
                System.out.println(key + ", \nold: null, \nnew:" + value);
            });
        }
        System.out.println("\n");
    }

    @Override
    public void close() {
        context = null;
        retValue = null;
        stmtSwitcher = null;
        initialedCallEdge.clear();
        triggerTimes.clear();
    }
}
