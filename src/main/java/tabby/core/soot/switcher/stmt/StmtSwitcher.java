package tabby.core.soot.switcher.stmt;

import lombok.Getter;
import lombok.Setter;
import soot.jimple.AbstractStmtSwitch;
import tabby.core.data.Context;
import tabby.core.soot.switcher.value.ValueSwitcher;
import tabby.neo4j.bean.ref.MethodReference;
import tabby.neo4j.cache.CacheHelper;

/**
 * @author wh1t3P1g
 * @since 2020/12/12
 */
@Getter
@Setter
public abstract class StmtSwitcher extends AbstractStmtSwitch {

    public Context context;
    public CacheHelper cacheHelper;
    public MethodReference methodRef;
    public ValueSwitcher leftValueSwitcher;
    public ValueSwitcher rightValueSwitcher;
}
