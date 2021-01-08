package tabby.core.soot.switcher.value;

import lombok.Getter;
import lombok.Setter;
import soot.jimple.AbstractJimpleValueSwitch;
import tabby.core.data.Context;
import tabby.core.data.DataContainer;
import tabby.core.data.TabbyVariable;
import tabby.db.bean.ref.MethodReference;

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

}
