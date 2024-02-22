package tabby.analysis.model;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.SemanticUtils;
import tabby.core.container.DataContainer;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * call边builder
 * 需要在分析当前invoke之前，把当前的所有信息保存起来
 * 所以在stmtSwitcher上用
 * @author wh1t3P1g
 * @since 2022/2/17
 */
@Slf4j
public class CallEdgeBuilder {

    private final LinkedList<Model> chains = new LinkedList<>();
    @Setter
    private List<Integer> pollutedPosition = new ArrayList<>();

    public CallEdgeBuilder() {
        chains.add(new IgnoreInvokeModel());
        chains.add(new DefaultInvokeModel());
    }

    public void build(Stmt stmt,
                      MethodReference caller, DataContainer dataContainer){
        try{

            InvokeExpr ie = SemanticUtils.getInvokeExpr(stmt);

            if(ie == null){
                log.error("get invoke expr error: {}", stmt);
                return;
            }

            MethodReference callee = dataContainer.getOrAddMethodRef(ie);

            if(callee == null){
                log.error("get callee error: {}", stmt);
                return;
            }

            for(Model model:chains){
                model.setPP(pollutedPosition);
                if(model.apply(stmt, false, caller, callee, dataContainer)){
                    break;
                }
            }
        } catch (Exception e){
            // 多线程的情况下 可能在soot提取method上会有一些问题
//            log.error(e.getMessage());
//            e.printStackTrace();
        }

    }
}
