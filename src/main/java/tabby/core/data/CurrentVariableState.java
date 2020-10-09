package tabby.core.data;

import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * @author wh1t3P1g
 * @since 2020/10/9
 */
@Data
public class CurrentVariableState<T> {

    private List<Set<T>> localVars;
    private List<Set<T>> stackVars;

}
