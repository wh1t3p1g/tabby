package tabby.core.discover.xstream;

import org.springframework.stereotype.Component;
import tabby.config.GlobalConfiguration;
import tabby.core.discover.Discover;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author wh1t3P1g
 * @since 2020/11/21
 */
@SuppressWarnings({"unchecked"})
@Component
public class SimpleXStreamGadgetDiscover implements Discover {
    private static Map<String, List<Map<String, Object>>> SOURCES = new HashMap<>();

    @Override
    public void run() {
        // 1. find all sinks functions
        // 2. back forward analysis with call graph, from sink to sources
        // 3. record a chain from sink to source
    }


    static {
        String json = "{\n" +
                "  \"java.lang.Object\": [{\"name\": \"hashCode\", \"param\": []},{\"name\": \"toString\", \"param\": []}],\n" +
                "  \"java.lang.Comparable\": [{\"name\": \"compareTo\", \"param\": []}]\n" +
                "}";
        SOURCES = (Map<String, List<Map<String, Object>>>) GlobalConfiguration.GSON.fromJson(json, Map.class);
    }
}
