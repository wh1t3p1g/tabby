package tabby.analysis.switcher;

import soot.jimple.AbstractStmtSwitch;
import tabby.analysis.data.Context;

import java.util.Map;
import java.util.Set;

/**
 * @author wh1t3P1g
 * @since 2022/1/8
 */
public abstract class StmtSwitcher extends AbstractStmtSwitch {
    protected Context context;
    protected ValueSwitcher valueSwitcher;
    protected Map<String, Integer> triggerTimes;
    public Map<String, Set<String>> actions = null;

    public abstract void accept(Context context);

    public void setTriggerTimes(Map<String, Integer> triggerTimes) {
        this.triggerTimes = triggerTimes;
    }

    public void setActions(Map<String, Set<String>> actions) {
        this.actions = actions;
    }
}
