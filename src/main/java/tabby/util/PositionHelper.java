package tabby.util;

import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;

/**
 * @author wh1t3P1g
 * @since 2022/2/8
 */
@Slf4j
public class PositionHelper {

    public static int THIS = -1;
    public static int SOURCE = -2;
    public static int NOT_POLLUTED_POSITION = -3;

    public static String getPosition(int pos){
        if(THIS == pos){
            return "this";
        }else if(SOURCE == pos){
            return "source"; // source暂时不用处理
        }else if(pos >= 0){
            return "param-"+pos;
        }else{
            return null;
        }
    }

    public static int getPosition(String pos){
        if(pos == null || pos.isEmpty()) return -3;
        if (pos.startsWith("this")) {
            return THIS;
        }else if(pos.startsWith("param-")){
            String realPos = pos;
            String[] data = realPos.split("\\|");
            try{
                return Integer.parseInt(data[0].split("-")[1]);
            }catch (Exception e){
                log.error("parse error: " + pos);
            }
        }
        else if(pos.startsWith("source")){
            return SOURCE;
        }
        return NOT_POLLUTED_POSITION;
    }

    public static Set<String> getPositions(Set<Integer> positions, int exclude){
        Set<String> pos = new HashSet<>();
        for(int p:positions){
            if(p == exclude) continue;
            String tmp = getPosition(p);
            if(tmp != null){
                pos.add(tmp);
            }
        }
        return pos;
    }

    public static Set<Integer> getPositions(Set<String> positions){
        Set<Integer> retData = new HashSet<>();
        if(positions != null){
            for(String pos:positions){
                retData.add(getPosition(pos));
            }
            retData.remove(NOT_POLLUTED_POSITION); // 剔除没用的-3
        }
        if(retData.isEmpty()){
            retData.add(NOT_POLLUTED_POSITION);
        }
        return retData;
    }

}
