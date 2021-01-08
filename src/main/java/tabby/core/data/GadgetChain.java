package tabby.core.data;

import lombok.Data;
import tabby.db.bean.ref.MethodReference;

import java.util.LinkedList;

/**
 * @author wh1t3P1g
 * @since 2020/12/10
 */
@Data
public class GadgetChain {

    private LinkedList<GadgetChain> next = new LinkedList<>();

    private String method;
    private String obj;
    private MethodReference methodRef;

    private GadgetChain preNode;
    private String invokerType;

    public GadgetChain(GadgetChain preNode){
        this.preNode = preNode;
    }

    public GadgetChain(){
    }

    public boolean isInRecursion(String invokeSignature) {
        if (invokeSignature.equals(method)) {
            return true;
        }
        if (preNode != null) {
            return preNode.isInRecursion(invokeSignature);
        }
        return false;
    }

    public boolean hasNext(){
        return next != null && !next.isEmpty();
    }

    public void print(StringBuffer sb){
        if(sb == null){
            sb = new StringBuffer();
        }
        sb.append(toString()).append("\n");
        if(preNode != null){
            StringBuffer sb1 = new StringBuffer();
            sb1.append(sb.toString());
            preNode.print(sb1);
        }else{
            System.out.print(sb);
        }
    }

    @Override
    public String toString() {
        return "GadgetChain{" +
                "method='" + method + '\'' +
                ", obj='" + obj + '\'' +
                ", invokerType='" + invokerType + '\'' +
                '}';
    }
}
