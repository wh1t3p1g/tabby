package tabby.core.discover.xstream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.SootMethod;
import tabby.config.GlobalConfiguration;
import tabby.neo4j.bean.ref.MethodReference;
import tabby.neo4j.cache.CacheHelper;
import tabby.neo4j.service.MethodRefService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author wh1t3P1g
 * @since 2020/11/21
 */
@SuppressWarnings({"unchecked"})
@Component
public class SimpleXStreamGadgetDiscover {
    private static Map<String, List<Map<String, Object>>> SOURCES = new HashMap<>();

    @Autowired
    private MethodRefService methodRefService;
    @Autowired
    private CacheHelper cacheHelper;

    public void run() {
        // 1. find all sinks functions
        List<String> sinkMethodSig = methodRefService.findAllSinks();
        for(String sink: sinkMethodSig){
            doAnalysis(sink);
        }
        // 2. back forward analysis with call graph, from sink to sources
        // 3. record a chain from sink to source
    }

    public void doAnalysis(String signature){
        List<String> called = methodRefService.findAllByInvokedMethodSignature(signature);
        called.forEach(calledMethodSignature -> {
            MethodReference methodRef = cacheHelper.loadMethodRef(calledMethodSignature);
            SootMethod method = methodRef.getCachedMethod();
        });
    }


    static {
        String json = "{\n" +
                "  \"java.lang.Object\": [{\"name\": \"hashCode\", \"param\": []},{\"name\": \"toString\", \"param\": []}],\n" +
                "  \"java.lang.Comparable\": [{\"name\": \"compareTo\", \"param\": []}]\n" +
                "}";
        SOURCES = (Map<String, List<Map<String, Object>>>) GlobalConfiguration.GSON.fromJson(json, Map.class);
    }
}
