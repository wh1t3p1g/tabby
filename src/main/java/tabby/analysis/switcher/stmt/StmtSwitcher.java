package tabby.analysis.switcher.stmt;

import lombok.Getter;
import lombok.Setter;
import soot.jimple.AbstractStmtSwitch;
import tabby.analysis.switcher.value.ValueSwitcher;
import tabby.common.bean.ref.MethodReference;
import tabby.analysis.data.Context;
import tabby.core.container.DataContainer;

/**
 * @author wh1t3P1g
 * @since 2020/12/12
 */
@Getter
@Setter
public abstract class StmtSwitcher extends AbstractStmtSwitch {

    public Context context;
    public DataContainer dataContainer;
    public MethodReference methodRef;
    public ValueSwitcher leftValueSwitcher;
    public ValueSwitcher rightValueSwitcher;
    public boolean reset = true;
}
