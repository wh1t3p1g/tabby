package tabby.analysis.model;

import com.google.common.collect.Sets;
import soot.Value;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import tabby.analysis.data.SimpleObject;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.PositionUtils;

import java.util.Set;

/**
 * @author wh1t3p1g
 * @since 2023/12/07
 */
public class XMLRPCSinkModel extends DefaultInvokeModel {
    @Override
    public boolean apply(Stmt stmt) {
        if ("org.xml.sax.XMLReader".equals(callee.getClassname())
                && "setContentHandler".equals(callee.getName())) {
            InvokeExpr ie = stmt.getInvokeExpr();
            Value value = ie.getArg(0);
            SimpleObject obj = container.getObject(value);
            Set<String> types = container.getObjectTypes(obj);
            if (types.contains("org.apache.xmlrpc.parser.XmlRpcRequestParser")
                    || types.contains("org.apache.xmlrpc.parser.XmlRpcResponseParser")) {

                MethodReference readObjectMethodRef
                        = dataContainer.getOrAddMethodRefBySubSignature("java.io.ObjectInputStream", "java.lang.Object readObject()");

                if (readObjectMethodRef != null) {
                    // 建立人工指向反序列化的边
                    positions.add(Sets.newHashSet(PositionUtils.THIS));
                    this.types.add(Sets.newHashSet("java.io.ObjectInputStream"));
                    setCallee(readObjectMethodRef);
                    setInvokeType("ManualInvoke");
                    setCallerThisFieldObj(false);
                    setLineNumber(stmt.getJavaSourceStartLineNumber());
                }
            }
        }

        // 继续传递建边
        return false;
    }

}
