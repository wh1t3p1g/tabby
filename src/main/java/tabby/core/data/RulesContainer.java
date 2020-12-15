package tabby.core.data;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tabby.config.GlobalConfiguration;
import tabby.util.FileUtils;

import java.io.FileNotFoundException;
import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/11/20
 */
@Slf4j
@Data
@Component
public class RulesContainer {

    private Map<String, List<Map<String, Object>>> sinks = new HashMap<>();
    private Map<String, List<String>> ignores = new HashMap<>();

    public RulesContainer() throws FileNotFoundException {
        loadSinks();
        loadIgnores();
    }

    public boolean isSink(String classname, String method){
        if(sinks.containsKey(classname)){
            List<Map<String, Object>> functions = sinks.get(classname);
            for(Map<String, Object> function:functions){
                if(method.equals(function.get("name"))){
                    return true;
                }
            }
        }
        return false;
    }

    public Map<String, String> getSinkParamPosition(String classname, String method){
        Map<String, String> retMap = new HashMap<>();
        if(sinks.containsKey(classname)){
            List<Map<String, Object>> functions = sinks.get(classname);
            for(Map<String, Object> function:functions){
                if(method.equals(function.get("name"))){
                    List<String> list = (List)function.get("related");
                    if(list == null){
                        continue;
                    }
                    for(String str:list){
                        retMap.put(str, "evil");
                    }
                    return retMap;
                }
            }
        }
        return retMap;
    }

    public boolean isIgnore(String classname, String method){
        if(ignores.containsKey(classname)){
            return ignores.get(classname).contains(method);
        }
        return false;
    }

    @SuppressWarnings({"unchecked"})
    private void loadSinks() throws FileNotFoundException {
        sinks = (Map<String, List<Map<String, Object>>>) FileUtils.getJsonContent(GlobalConfiguration.SINKS_PATH, Map.class);
        if(sinks == null){
            throw new FileNotFoundException("Sink File Not Found");
        }
        log.info("load "+ sinks.size() +" sinks success!");
    }

    @SuppressWarnings({"unchecked"})
    private void loadIgnores() throws FileNotFoundException {
        ignores = (Map<String, List<String>>) FileUtils.getJsonContent(GlobalConfiguration.IGNORES_PATH, Map.class);
        if(ignores == null){
            throw new FileNotFoundException("Ignore File Not Found");
        }
        log.info("load "+ ignores.size() +" ignore success!");
    }
}
