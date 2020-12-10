package tabby.core.discover;

import tabby.core.data.GadgetChain;

import java.util.List;

/**
 * @author wh1t3P1g
 * @since 2020/11/21
 */
public interface Discover {

    void getSinks();

    void getSources();

    List<String> analysis(GadgetChain gadgetChain);

    void transfer();

    void run();
}
