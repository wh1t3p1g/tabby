package tabby.analysis.data;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import soot.Local;
import soot.SootFieldRef;
import soot.Value;
import soot.jimple.*;
import tabby.common.utils.SemanticUtils;
import tabby.config.GlobalConfiguration;

import java.util.Objects;

import static tabby.analysis.ActionWorker.*;

/**
 * @author wh1t3p1g
 * @since 2022/5/7
 */
@Data
@Slf4j
public class Action {

    private boolean hasField;
    private boolean isArray;
    private boolean isStatus;
    private boolean isMerge;

    private String identify; // this param-n source

    private Action subAction;

    public static Action newInstance() {
        return new Action();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(identify);

        if (isArray) {
            sb.append(ARRAY_FEATURE);
        } else if (isMerge) {
            sb.append(MERGE_FEATURE);
        } else if (isStatus) {
            sb.append(STATUS_FEATURE);
        }

        if (hasField && subAction != null) {
            sb.append(FIELD_FEATURE);
            sb.append(subAction);
        }
        return sb.toString();
    }

    public boolean isNull() {
        return identify == null || identify.isEmpty();
    }

    private String toString(int current, int start, int end) {
        StringBuilder sb = new StringBuilder();
        if (current >= start) {
            sb.append(identify);

            if (isArray) {
                sb.append(ARRAY_FEATURE);
            } else if (isMerge && !hasField) {
                sb.append(MERGE_FEATURE);
            } else if (isStatus && !hasField) {
                sb.append(STATUS_FEATURE);
            }
        }

        if (current < end && hasField && subAction != null) {
            sb.append(FIELD_FEATURE);
            sb.append(subAction.toString(current + 1, start, end));
        }
        String ret = sb.toString();
        if (ret.startsWith(FIELD_FEATURE)) {
            ret = ret.substring(3);
        }
        return ret;
    }

    public String toString(int start, int end) {
        if (start < 0) {
            start = 0;
        }
        String ret = toString(0, start, end);
        if (ret.startsWith(FIELD_FEATURE)) {
            ret = ret.substring(3);
        }
        return ret;
    }

    public static Action parse(String action) {
        if (action == null) return null;
        Action ret = new Action();
        String temp = action;
        if (temp.startsWith(FIELD_FEATURE)) {
            temp = temp.substring(3);
        }
        boolean isField = temp.contains(FIELD_FEATURE);
        if (isField) {
            String[] acts = action.split(FIELD_FEATURE, 2);
            ret.setSubAction(parse(acts[1]));
            ret.setHasField(true);
            temp = acts[0];
        }

        ret.setIdentify(SemanticUtils.cleanTag(temp, MERGE_FEATURE, STATUS_FEATURE, ARRAY_FEATURE));
        boolean isStatus = temp.contains(STATUS_FEATURE);
        boolean isArray = temp.contains(ARRAY_FEATURE);
        boolean isMerge = temp.contains(MERGE_FEATURE);
        // 三者是互斥的关系，优先级其次如下所示
        if (isArray) { // TODO
            ret.setArray(true);
        } else if (isMerge && !isField) {
            ret.setMerge(true);
        } else if (isStatus && !isField) {
            ret.setStatus(true);
        }
        // this<s><f>field 这种情况会被处理成 this<f>field
        // 在后续查找对应的pts时，会对查找 this this<s> this<m> 依次查找

        return ret;
    }

    public static Action makeInstanceFieldAction(String identify, String fieldIdentify) {
        Action ret = new Action();
        ret.setIdentify(identify);
        ret.setHasField(true);
        Action subAction = Action.newInstance();
        subAction.setIdentify(fieldIdentify);
        ret.setSubAction(subAction);
        return ret;
    }

    public static Action makeAction(String identify, boolean isArray, boolean isMerge, boolean isStatus) {
        Action ret = new Action();
        ret.setIdentify(identify);
        ret.setArray(isArray);
        ret.setMerge(isMerge);
        ret.setStatus(isStatus);
        return ret;
    }

    public Action clone() {
        Action action = new Action();
        action.setIdentify(identify);
        action.setArray(isArray);
        action.setMerge(isMerge);
        action.setHasField(hasField);
        action.setStatus(isStatus);
        if (hasField && subAction != null) {
            action.setSubAction(subAction.clone());
        }
        return action;
    }

    public Action clone(int depth) {
        if (depth < 0) return null;
        Action action = new Action();
        action.setIdentify(identify);
        action.setArray(isArray);
        action.setMerge(isMerge);
        action.setHasField(hasField);
        action.setStatus(isStatus);
        if (hasField && subAction != null) {
            action.setSubAction(subAction.clone(depth - 1));
        }
        return action;
    }

    public Action cloneSelf() {
        Action action = new Action();
        action.setIdentify(identify);
        action.setArray(isArray);
        action.setMerge(isMerge);
        action.setHasField(hasField);
        action.setStatus(isStatus);
        return action;
    }


    public Action[] split(int index) {
        Action[] actions = new Action[2];
        Action nextAction = this;
        int pos = 0;
        Action posAction = null;
        while (nextAction != null) {
            if (pos <= index) {
                if (actions[0] == null) {
                    actions[0] = nextAction.cloneSelf();
                    posAction = actions[0];
                } else {
                    Action temp = nextAction.cloneSelf();
                    posAction.setSubAction(temp);
                    posAction = temp;
                }
            } else {
                if (actions[1] == null) {
                    actions[1] = nextAction.cloneSelf();
                    posAction = actions[1];
                } else {
                    Action temp = nextAction.cloneSelf();
                    posAction.setSubAction(temp);
                    posAction = temp;
                }
            }
            pos++;
            nextAction = nextAction.subAction;
        }
        return actions;
    }


    public boolean isEndWithArray() {
        if (subAction != null) {
            return subAction.isEndWithArray();
        } else {
            return isArray;
        }
    }

    public boolean isEndWithMerge() {
        if (subAction != null) {
            return subAction.isEndWithMerge();
        } else {
            return isMerge;
        }
    }

    public boolean isEndWithStatus() {
        if (subAction != null) {
            return subAction.isEndWithStatus();
        } else {
            return isStatus;
        }
    }

    public void setStatus(boolean flag) {
        if (subAction != null) {
            subAction.setStatus(flag);
        } else {
            isStatus = flag;
        }
    }

    public void setSelfStatus(boolean flag) {
        isStatus = flag;
    }

    public void setMerge(boolean flag) {
        if (subAction != null) {
            subAction.setMerge(flag);
        } else {
            isMerge = flag;
        }
    }

    public void setSelfMerge(boolean flag) {
        isMerge = flag;
    }

    public void setArray(boolean flag) {
        if (subAction != null) {
            subAction.setArray(flag);
        } else {
            isArray = flag;
        }
    }

    public void setSelfArray(boolean flag) {
        isArray = flag;
    }

    public Action getEndAction() {
        Action action = this;
        while (action.hasField && action.subAction != null) {
            action = action.subAction;
        }
        return action;
    }

    public boolean isStartWithParam() {
        return identify.startsWith(PARAM_PREFIX);
    }

    /**
     * index 从1开始
     * this<f>test<f>test1
     * 1 为 test
     * 2 为 test1
     *
     * @param index
     * @return
     */
    public String getField(int index) {
        String ret = null;
        Action action = this;
        while (action.hasField && index > 0) {
            action = action.subAction;
            index--;
            if (index == 0) {
                ret = action.identify;
            }
        }
        return ret;
    }

    public int getFieldLength() {
        int ret = 0;
        Action action = this;
        while (action != null && action.hasField) {
            action = action.subAction;
            ret++;
        }
        return ret;
    }

    public boolean isOverMaxFieldSize() {
        return getFieldLength() > GlobalConfiguration.OBJECT_FIELD_K_LIMIT;
    }

    public Action popLeft(){
        return subAction.clone();
    }

    public Action popRight(){
        Action action = this.clone();
        Action preAction = null;
        while (action.hasField && action.subAction != null) {
            preAction = action;
            action = action.subAction;
        }
        preAction.subAction = null;
        preAction.hasField = false;
        return preAction;
    }

    public void append(Action action) {
        if(action != null){
            Action end = getEndAction();
            int length = action.getFieldLength();
            Action cur = action;
            for(int i=0;i<length;i++){
                if(end.getIdentify().equals(cur.getIdentify())){
                    cur = action.subAction;
                }
            }
            if(cur != null && !end.getIdentify().equals(cur.getIdentify())){
                end.setHasField(true);
                end.setSubAction(cur);
            }
        }
        // check new action length
        if(isOverMaxFieldSize()){
            Action cur = this;
            for(int i=0;i<GlobalConfiguration.OBJECT_FIELD_K_LIMIT;i++){
                if(cur != null){
                    cur = cur.subAction;
                }else{
                    break;
                }
            }
            // 截断超长的action
            if(cur != null){
                cur.subAction = null;
                cur.setHasField(false);
            }
        }
    }

    /**
     * return a,a<f>b,null
     *
     * @param value
     * @return
     */
    public static String getSimpleName(Value value) {
        Action action = null;
        if (value instanceof InstanceFieldRef) {
            Value base = ((InstanceFieldRef) value).getBase();
            String baseName = getSimpleName(base);
            SootFieldRef ref = ((InstanceFieldRef) value).getFieldRef();
            action = Action.makeInstanceFieldAction(baseName, ref.name());
        } else if (value instanceof Local) {
            action = Action.makeAction(((Local) value).getName(), false, false, false);
        } else if (value instanceof ArrayRef) {
            Value base = ((ArrayRef) value).getBase();
            return getSimpleName(base);
        } else if (value instanceof CastExpr) {
            Value base = ((CastExpr) value).getOp();
            return getSimpleName(base);
        } else if (value instanceof StaticFieldRef || value instanceof Constant) {
            action = Action.makeAction(value.toString(), false, false, false);
        } else {
            log.debug("getSimpleName:" + value.toString());
        }

        if (action == null) {
            return null;
        } else {
            return action.toString();
        }
    }

    public static Action getSimpleAction(Value value) {
        Action action = null;
        if (value instanceof InstanceFieldRef) {
            Value base = ((InstanceFieldRef) value).getBase();
            String baseName = getSimpleName(base);
            SootFieldRef ref = ((InstanceFieldRef) value).getFieldRef();
            action = Action.makeInstanceFieldAction(baseName, ref.name());
        } else if (value instanceof Local) {
            action = Action.makeAction(((Local) value).getName(), false, false, false);
        } else if (value instanceof ArrayRef) {
            Value base = ((ArrayRef) value).getBase();
            return getSimpleAction(base);
        } else if (value instanceof CastExpr) {
            Value base = ((CastExpr) value).getOp();
            return getSimpleAction(base);
        } else if (value instanceof StaticFieldRef || value instanceof Constant) {
            action = Action.makeAction(value.toString(), false, false, false);
        } else {
            log.debug("getSimpleName:" + value.toString());
        }

        if (action == null) {
            return Action.newInstance();
        } else {
            return action;
        }
    }

    public static void main(String[] args) {
        Action action = makeInstanceFieldAction("param-0", "test");
        System.out.println(action);
        Action a1 = Action.parse("this<s><m><f>test<a><f>admin<a>");
        Action a2 = Action.parse("test1<a><f>admin1<a>");
        System.out.println(a1.getFieldLength());
        System.out.println(a1.getField(2));
        System.out.println("toString:" + a1.toString(1, 2));
        System.out.println("toString:" + a1.split(2)[0]);
        System.out.println("toString:" + a1.split(2)[1]);
        a1.append(a2);
        System.out.println(a1);
        Action a3 = Action.parse("$r4<f>prev<f>prev<f>prev<f>prev<f>prev<f>");
        Action a4 = Action.parse("$r5<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev<f>prev");
        a3.append(a4.getSubAction());
        System.out.println(a3.toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Action action = (Action) o;
        return hasField == action.hasField && isArray == action.isArray && isStatus == action.isStatus && isMerge == action.isMerge && Objects.equals(identify, action.identify) && Objects.equals(subAction, action.subAction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hasField, isArray, isStatus, isMerge, identify, subAction);
    }
}
