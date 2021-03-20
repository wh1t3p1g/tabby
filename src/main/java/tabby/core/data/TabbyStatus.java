package tabby.core.data;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * @author wh1t3P1g
 * @since 2021/3/19
 */
@Data
public class TabbyStatus {
    // polluted
    boolean isPolluted = false;
    // polluted positions like param-0,param-1,field-name1,this
    Set<String> types = new HashSet<>();

    public void setType(String type){
        types.clear();
        types.add(type);
    }

    public void addType(String type){
        types.add(type);
    }

    public void concatType(String type){
        Set<String> newTypes = new HashSet<>();
        for(String old:types){
            newTypes.add(String.format("{}|{}", old, type));
        }
    }

    /**
     * 只获取第一个polluted type
     * 当存在多个polluted type时，获取第一个，做近似化处理
     * @return
     */
    public String getFirstPollutedType(){
        if(!isPolluted) return null;
        for(String type:types){
            if(type.startsWith("this") || type.startsWith("param-")){
                return type;
            }
        }
        return null;
    }

    public TabbyStatus clone(){
        TabbyStatus status = new TabbyStatus();
        status.setPolluted(isPolluted);
        status.setTypes(types);
        return status;
    }
}
