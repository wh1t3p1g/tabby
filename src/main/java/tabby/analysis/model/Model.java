package tabby.analysis.model;

import com.google.common.collect.ImmutableMap;
import soot.SootClass;
import soot.Type;
import soot.Value;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import tabby.analysis.container.ValueContainer;
import tabby.common.bean.edge.Call;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.PositionUtils;
import tabby.common.utils.SemanticUtils;
import tabby.core.container.DataContainer;

import java.util.*;

/**
 * @author wh1t3p1g
 * @since 2021/8/31
 */
public interface Model {

    static Map<String, String> SUB_SIGNATURES
            = ImmutableMap.of(
            "RUNNABLE_RUN", "void run()", // runnable.run
            "P_ACTION", "java.lang.Object run()", // PrivilegedAction.run
            "CALLABLE_RUN", "void call()",
            "PROXY_INVOKE", "java.lang.Object invoke(java.lang.Object,java.lang.reflect.Method,java.lang.Object[])"
    );

    void doInit(MethodReference caller, MethodReference callee, ValueContainer container, DataContainer dataContainer);

    boolean apply(Stmt stmt);

    void doFinal(Set<Call> callEdges);

    default String getSubSignature(String classname, String func, String bytecodeParams, String defaultSignature) {
        // 不是很有必要
//        try{
//            SootClass cls = Scene.v().getSootClass(classname);
//            if(cls.getMethodCount() > 0){
//                for(SootMethod method:cls.getMethods()){
//                    if(func.equals(method.getName())
//                            && bytecodeParams.equals(method.getBytecodeParms())){ // 找到的第一个符合要求的名字
//                        return method.getSubSignature();
//                    }
//                }
//            }
//        }catch (Exception e){
//            // class not found
//        }
        return SUB_SIGNATURES.get(defaultSignature);
    }


    default Type getArgType(InvokeExpr ie, int index) {
        try {
            Value arg = ie.getArg(index);
            if (arg != null) {
                return arg.getType();
            }
        } catch (Exception e) {
            // class not found
        }
        return null;
    }

    default boolean isAssignableFrom(String target, Class<?> cls, DataContainer dataContainer) {

        if (target.equals(cls.getName())) return true;

        try {
            SootClass targetClass = SemanticUtils.getSootClass(target);
            Set<String> allFatherNodes = SemanticUtils.getAllFatherNodes(targetClass, true);
            return allFatherNodes.contains(cls.getName());
        } catch (Exception ignored) {
        }

        return false;
    }

    void setContainer(ValueContainer container);

    default List<Set<Integer>> mergePositions(List<Set<Integer>> origin){
        Set<Integer> set = new HashSet<>();
        for (Set<Integer> pos : origin) {
            set.addAll(pos);
        }
        set.remove(PositionUtils.NOT_POLLUTED_POSITION);
        if (set.isEmpty()) {
            set.add(PositionUtils.NOT_POLLUTED_POSITION);
        }
        List<Set<Integer>> positions = new LinkedList<>();
        positions.add(set);
        return positions;
    }
}
