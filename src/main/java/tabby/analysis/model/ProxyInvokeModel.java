package tabby.analysis.model;

import com.google.common.collect.Sets;
import soot.Type;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import tabby.analysis.data.SimpleObject;
import tabby.analysis.helper.SemanticHelper;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.PositionUtils;

import java.util.List;
import java.util.Set;

/**
 * @author wh1t3p1g
 * @since 2021/8/31
 */
public class ProxyInvokeModel extends DefaultInvokeModel {
    @Override
    public boolean apply(Stmt stmt) {
        if ("java.lang.reflect.Proxy".equals(callee.getClassname())
                && "newProxyInstance".equals(callee.getName())) {

            Type type = getArgType(stmt.getInvokeExpr(), 2);
            if (type != null) {
                String classname = type.toString();
                String subSignature
                        = getSubSignature(classname, "invoke", "Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;", "PROXY_INVOKE");

                MethodReference invokeMethodRef
                        = dataContainer.getOrAddMethodRefBySubSignature(classname, subSignature);

                if (invokeMethodRef != null) {
                    InvokeExpr ie = stmt.getInvokeExpr();
                    // get positions && types
                    List<Set<Integer>> pos = SemanticHelper.extractPositionsFromInvokeExpr(ie, container);
                    positions.add(pos.get(3));
                    positions.add(pos.get(3));
                    positions.add(Sets.newHashSet(PositionUtils.NOT_POLLUTED_POSITION));
                    positions.add(pos.get(3));

                    SimpleObject obj = SemanticHelper.extractObjectFromInvokeExpr(ie, 2, container);
                    Set<String> objTypes = container.getTypes(obj);
                    objTypes.add(classname);

                    types.add(objTypes);
                    types.add(Sets.newHashSet("java.lang.Object"));
                    types.add(Sets.newHashSet("java.lang.reflect.Method"));
                    types.add(Sets.newHashSet("java.lang.Object"));
                    // setters
                    setCallee(invokeMethodRef);
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
