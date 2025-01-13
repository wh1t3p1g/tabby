package tabby.analysis.model;

import soot.Type;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import tabby.analysis.data.SimpleObject;
import tabby.analysis.helper.SemanticHelper;
import tabby.common.bean.ref.MethodReference;

import java.util.Set;

/**
 * @author wh1t3p1g
 * @since 2021/8/31
 */
public class AccessControllerInvokeModel extends DefaultInvokeModel {
    @Override
    public boolean apply(Stmt stmt) {
        if ("java.security.AccessController".equals(callee.getClassname())
                && "doPrivileged".equals(callee.getName())) {

            InvokeExpr ie = stmt.getInvokeExpr();
            Type type = getArgType(ie, 0);
            if (type != null) {
                String classname = type.toString();
                String subSignature
                        = getSubSignature(classname, "run", "", "P_ACTION");

                MethodReference runMethodRef
                        = dataContainer.getOrAddMethodRefBySubSignature(classname, subSignature);

                if (runMethodRef != null) {
                    // get positions && types
                    positions = SemanticHelper.extractPositionsFromInvokeExpr(ie, container);
                    positions = mergePositions(positions);

                    SimpleObject obj = SemanticHelper.extractObjectFromInvokeExpr(ie, 0, container);
                    Set<String> objTypes = container.getTypes(obj);
                    objTypes.add(classname);

                    types.add(objTypes);
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
