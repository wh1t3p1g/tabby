package tabby.core.soot.switcher.value;

import lombok.Getter;
import lombok.Setter;
import soot.jimple.AbstractJimpleValueSwitch;
import tabby.core.data.Context;
import tabby.core.data.TabbyVariable;
import tabby.neo4j.bean.ref.MethodReference;
import tabby.neo4j.cache.CacheHelper;

/**
 * @author wh1t3P1g
 * @since 2020/12/12
 */
@Getter
@Setter
public abstract class ValueSwitcher extends AbstractJimpleValueSwitch {

    public Context context;
    public CacheHelper cacheHelper;
    public MethodReference methodRef;
    public TabbyVariable rvar;
    public boolean unbind = false;
    public boolean reset = true;

}
