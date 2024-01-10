package tabby.analysis.switcher.value;

import lombok.Getter;
import lombok.Setter;
import soot.Unit;
import soot.jimple.AbstractJimpleValueSwitch;
import tabby.common.bean.ref.MethodReference;
import tabby.analysis.data.Context;
import tabby.core.container.DataContainer;
import tabby.analysis.data.TabbyVariable;

/**
 * @author wh1t3P1g
 * @since 2020/12/12
 */
@Getter
@Setter
public abstract class ValueSwitcher extends AbstractJimpleValueSwitch {

    public Context context;
    public DataContainer dataContainer;
    public MethodReference methodRef;
    public TabbyVariable rvar;
    public boolean unbind = false;
    public boolean reset = true;
    public Unit unit;

}
