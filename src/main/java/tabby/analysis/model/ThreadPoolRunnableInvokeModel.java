package tabby.analysis.model;

import soot.Type;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import tabby.analysis.data.SimpleObject;
import tabby.analysis.helper.SemanticHelper;
import tabby.common.bean.ref.MethodReference;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * @author wh1t3p1g
 * @since 2021/9/15
 */
public class ThreadPoolRunnableInvokeModel extends DefaultInvokeModel {

    @Override
    public boolean apply(Stmt stmt) {
        String targetCls = callee.getClassname();

        if (targetCls == null) {
            return false;
        }

        String classname = null;
        String subSignature = null;
        if (isAssignableFrom(targetCls, Executor.class, dataContainer)
                && "execute".equals(callee.getName())) {
            Type type = getArgType(stmt.getInvokeExpr(), 0);
            if (type != null) {
                classname = type.toString();
                subSignature
                        = getSubSignature(classname, "run", "", "RUNNABLE_RUN");
            }
        } else if (isAssignableFrom(targetCls, ExecutorService.class, dataContainer)
                && "submit".equals(callee.getName())) {
            Type type = getArgType(stmt.getInvokeExpr(), 0);

            if (type != null) {
                classname = type.toString();

                if (isAssignableFrom(classname, Runnable.class, dataContainer)) { // java.lang.Runnable#run
                    subSignature = getSubSignature(classname, "run", "", "RUNNABLE_RUN");
                } else if (isAssignableFrom(classname, Callable.class, dataContainer)) { // java.util.concurrent.Callable#call
                    subSignature = getSubSignature(classname, "call", "", "CALLABLE_RUN");
                }
            }
        }

        if (classname != null && subSignature != null) {
            MethodReference runMethodRef
                    = dataContainer.getOrAddMethodRefBySubSignature(classname, subSignature);
            if (runMethodRef != null) {
                InvokeExpr ie = stmt.getInvokeExpr();
                // get positions && types
                List<Set<Integer>> pos = SemanticHelper.extractPositionsFromInvokeExpr(ie, container);
                SimpleObject obj = SemanticHelper.extractObjectFromInvokeExpr(ie, 0, container);
                Set<String> objTypes = container.getTypes(obj);
                objTypes.add(classname);
                positions = mergePositions(pos);
                types.add(objTypes);
                // setters
                setCallee(runMethodRef);
                setInvokeType("ManualInvoke");
                setCallerThisFieldObj(false);
                setLineNumber(stmt.getJavaSourceStartLineNumber());
                return true;
            }
        }

        return false;
    }

}
