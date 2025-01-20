package tabby.analysis.model;

import com.google.common.collect.Sets;
import soot.Type;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import tabby.analysis.data.SimpleObject;
import tabby.analysis.helper.SemanticHelper;
import tabby.common.bean.ref.MethodReference;

import java.util.List;
import java.util.Set;

/**
 * @author wh1t3p1g
 * @since 2021/9/15
 */
public class ThreadRunnableInvokeModel extends DefaultInvokeModel {

    @Override
    public boolean apply(Stmt stmt) {
        if ("java.lang.Thread".equals(callee.getClassname())
                && "<init>".equals(callee.getName()) && callee.isHasParameters()) {
            String classname = null;
            String subSignature = null;
            int index = -1;
            for (int i = 0; i < callee.getParameterSize(); i++) {
                Type type = getArgType(stmt.getInvokeExpr(), i);

                if (type != null && isAssignableFrom(type.toString(), Runnable.class, dataContainer)) {
                    classname = type.toString();
                    subSignature = getSubSignature(classname, "run", "", "RUNNABLE_RUN");
                    index = i;
                    break;
                }
            }

            if (classname != null && subSignature != null) {
                MethodReference runMethodRef =
                        dataContainer.getOrAddMethodRefBySubSignature(classname, subSignature);

                if (runMethodRef != null) {
                    InvokeExpr ie = stmt.getInvokeExpr();
                    // get positions && types
                    List<Set<Integer>> pos = SemanticHelper.extractPositionsFromInvokeExpr(ie, container);
                    positions = mergePositions(pos);

                    if(index != -1){
                        SimpleObject obj = SemanticHelper.extractObjectFromInvokeExpr(ie, index, container);
                        Set<String> objTypes = container.getTypes(obj);
                        objTypes.add(classname);
                        types.add(objTypes);
                    }else{
                        types.add(Sets.newHashSet(classname));
                    }

                    // setters
                    setCallee(runMethodRef);
                    setInvokeType("ManualInvoke");
                    setCallerThisFieldObj(false);
                    setLineNumber(stmt.getJavaSourceStartLineNumber());
                    return true;
                }
            }
        }

        return false;
    }
}
