package tabby.core.discover;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tabby.core.data.GadgetChain;
import tabby.neo4j.cache.CacheHelper;
import tabby.neo4j.service.MethodRefService;

import java.util.ArrayList;
import java.util.List;

/**
 * @author wh1t3P1g
 * @since 2020/12/10
 */
@Component
public abstract class BackForwardedDiscover implements Discover{

    @Autowired
    private MethodRefService methodRefService;
    @Autowired
    private CacheHelper cacheHelper;

    private List<String> sinks;
    private List<String> sources;
    private List<GadgetChain> gadgetChains;

    @Override
    public void run() {
        gadgetChains = new ArrayList<>();
        getSinks();
        getSources();

        if(sinks != null && !sinks.isEmpty()){
            for(String sink:sinks){

            }
        }
    }

    @Override
    public void getSinks() {
        sinks = methodRefService.findAllSinks();
    }
}
