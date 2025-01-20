package tabby.analysis.model;

import lombok.extern.slf4j.Slf4j;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import tabby.analysis.container.ValueContainer;
import tabby.common.bean.edge.Call;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.SemanticUtils;
import tabby.core.container.DataContainer;

import java.util.LinkedList;
import java.util.Set;

/**
 * call边builder
 * 需要在分析当前invoke之前，把当前的所有信息保存起来
 * 所以在stmtSwitcher上用
 *
 * @author wh1t3P1g
 * @since 2022/2/17
 */
@Slf4j
public class CallEdgeBuilder {

    private final LinkedList<Model> chains = new LinkedList<>();

    public CallEdgeBuilder() {
        chains.add(new IgnoreInvokeModel());
        chains.add(new AccessControllerInvokeModel());
        chains.add(new ThreadPoolRunnableInvokeModel());
        chains.add(new ThreadRunnableInvokeModel());
        chains.add(new ProxyInvokeModel());
        chains.add(new XMLRPCSinkModel());
        chains.add(new DefaultInvokeModel());
    }

    public void build(Stmt stmt,
                      MethodReference caller, Set<Call> callEdges,
                      ValueContainer container, DataContainer dataContainer) {
        try {

            InvokeExpr ie = SemanticUtils.getInvokeExpr(stmt);

            if (ie == null) {
                log.error("get invoke expr error: {}", stmt);
                return;
            }

            MethodReference callee = dataContainer.getOrAddMethodRef(ie);

            if (callee == null) {
                log.error("get callee error: {}", stmt);
                return;
            }

            for (Model model : chains) {
                model.doInit(caller, callee, container, dataContainer);
                if (model.apply(stmt)) {
                    model.doFinal(callEdges);
                    break;
                }
            }
        } catch (Exception e) {
            // 多线程的情况下 可能在soot提取method上会有一些问题
//            log.error(e.getMessage());
//            e.printStackTrace();
        }

    }
}
