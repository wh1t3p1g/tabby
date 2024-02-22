package tabby.analysis.model;

import soot.SootClass;
import soot.Type;
import soot.Value;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.SemanticUtils;
import tabby.core.container.DataContainer;

import java.util.List;
import java.util.Set;

/**
 * @author wh1t3p1g
 * @since 2021/8/31
 */
public interface Model {

    boolean apply(Stmt stmt, boolean isManual, MethodReference methodRef,
                  MethodReference targetMethodRef, DataContainer dataContainer);

    void setPP(List<Integer> pollutedPosition);

    default Type getArgType(InvokeExpr ie, int index){
        try{
            Value arg = ie.getArg(index);
            if(arg != null) {
                return arg.getType();
            }
        }catch (Exception e){
            // class not found
        }
        return null;
    }

    default boolean isAssignableFrom(String target, Class<?> cls, DataContainer dataContainer){
        if(target.equals(cls.getName())) return true;

        try{
            SootClass targetClass = SemanticUtils.getSootClass(target);
            Set<String> allFatherNodes = SemanticUtils.getAllFatherNodes(targetClass, true);
            return allFatherNodes.contains(cls.getName());
        }catch (Exception ignored){
        }

        return false;
    }

}
