package tabby.core.data;

import lombok.Data;

import java.util.LinkedList;

/**
 * @author wh1t3P1g
 * @since 2020/12/10
 */
@Data
public class GadgetChain {

    private LinkedList<GadgetChain> next;

    private String data;

    private GadgetChain head;

    private GadgetChain end;

    public GadgetChain(GadgetChain head, String data){
        this.head = head;
        this.data = data;
        this.head.setEnd(this);
    }

    public GadgetChain(String data){
        this.head = this;
        this.data = data;
        this.head.setEnd(this);
    }

    public boolean hasNext(){
        return next != null && !next.isEmpty();
    }

    public void print(StringBuffer sb){
        if(sb == null){
            sb = new StringBuffer();
        }
        sb.append(data);
        sb.append("\n");
        if(hasNext()){
            for (GadgetChain gadgetChain : next) {
                StringBuffer sb1 = new StringBuffer();
                sb1.append(sb.toString());
                gadgetChain.print(sb1);
            }
        }else{
            System.out.print(sb);
        }
    }
}
