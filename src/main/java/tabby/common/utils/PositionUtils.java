package tabby.common.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author wh1t3P1g
 * @since 2022/2/8
 */
@Slf4j
public class PositionUtils {

    public final static int THIS = -1;
    public final static int SOURCE = -2;
    public final static int NOT_POLLUTED_POSITION = -3;
    public final static int DAO = -4;
    public final static int RPC = -5;
    public final static int AUTH = -6;
    public final static int NULL_TYPE = -7;

    public final static String THIS_STRING = "this";
    public final static String SOURCE_STRING = "source";
    public final static String DAO_STRING = "dao";
    public final static String RPC_STRING = "rpc";
    public final static String AUTH_STRING = "auth";
    public final static String PARAM_STRING = "param-";
    public final static String NULL_STRING = "null_type";

    public static String getPosition(int pos) {
        if (THIS == pos) {
            return THIS_STRING;
        } else if (SOURCE == pos) {
            return SOURCE_STRING; // source暂时不用处理
        } else if (pos >= 0) {
            return PARAM_STRING + pos;
        } else if (DAO == pos) {
            return DAO_STRING;
        } else if (RPC == pos) {
            return RPC_STRING;
        } else if (AUTH == pos) {
            return AUTH_STRING;
        } else {
            return null;
        }
    }

    public static int getPosition(String pos) {
        if (pos == null || pos.isEmpty()) return -3;
        if (pos.startsWith(THIS_STRING)) {
            return THIS;
        } else if (pos.startsWith(PARAM_STRING)) {
            String realPos = pos;
            if (pos.contains("<a>")) {
                realPos = realPos.replace("<a>", "");
            }

            if (realPos.contains("<f>")) {
                realPos = realPos.split("<f>")[0];
            }

            String[] data = realPos.split("-");

            try {
                if (data[1].contains("..")) {
                    return NOT_POLLUTED_POSITION;
                }
                return Integer.parseInt(data[1]);
            } catch (Exception e) {
                log.error("parse error: " + pos);
            }
        } else if (pos.startsWith(SOURCE_STRING)) {
            return SOURCE;
        } else if (pos.startsWith(DAO_STRING)) {
            return DAO;
        } else if (pos.startsWith(RPC_STRING)) {
            return RPC;
        } else if (pos.startsWith(AUTH_STRING)) {
            return AUTH;
        } else if(pos.equals(NULL_STRING)){
            return NULL_TYPE;
        }
        return NOT_POLLUTED_POSITION;
    }

    public static int[] getMultiParamPositions(String pos) {
        int[] ret = new int[2];
        String realPos = pos;
        if (pos.contains("<a>")) {
            realPos = realPos.replace("<a>", "");
        }

        if (realPos.contains("<f>")) {
            realPos = realPos.split("<f>")[0];
        }

        String[] data = realPos.split("-");
        String[] startAndEnd = data[1].split("\\.\\.");
        ret[0] = Integer.parseInt(startAndEnd[0]);
        if ("n".equals(startAndEnd[1])) {
            ret[1] = THIS;
        } else {
            ret[1] = Integer.parseInt(startAndEnd[1]);
        }
        return ret;
    }

    public static Set<String> getPositions(Set<Integer> positions, int exclude) {
        Set<String> pos = new HashSet<>();
        for (int p : positions) {
            if (p == exclude) continue;
            String tmp = getPosition(p);
            if (tmp != null) {
                pos.add(tmp);
            }
        }
        return pos;
    }

    public static Set<String> getPositions(Set<Integer> positions, Collection<String> table) {
        Set<String> pos = new HashSet<>();
        for (int p : positions) {
            String tmp = getPosition(p);
            if (tmp != null && table.contains(tmp)) {
                pos.add(tmp);
            }
        }
        return pos;
    }

    public static Set<Integer> getPositions(Set<String> positions) {
        Set<Integer> retData = new HashSet<>();
        if (positions != null) {
            for (String pos : positions) {
                retData.add(getPosition(pos));
            }
            retData.remove(NOT_POLLUTED_POSITION); // 剔除没用的-3
        }
        if (retData.isEmpty()) {
            retData.add(NOT_POLLUTED_POSITION);
        }
        return retData;
    }

    public static boolean isSpecialAction(String action) {
        return action.startsWith(AUTH_STRING) || action.startsWith(SOURCE_STRING) || action.startsWith(DAO_STRING) || action.startsWith(RPC_STRING);
    }
}
