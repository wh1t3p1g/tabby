package tabby.analysis.helper;

import lombok.extern.slf4j.Slf4j;
import soot.SootClass;
import soot.Type;
import soot.Value;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import tabby.analysis.container.ValueContainer;
import tabby.analysis.data.SimpleObject;
import tabby.analysis.data.SimpleValue;
import tabby.common.utils.PositionUtils;

import java.util.*;

import static tabby.common.utils.SemanticUtils.*;

/**
 * @author wh1t3p1g
 * @since 2022/1/7
 */
@Slf4j
public class SemanticHelper {


    private static List<String> ENDPOINT_BLACKLIST =
            Arrays.asList("javax.servlet.Servlet",
                    "javax.servlet.http.HttpServlet",
                    "javax.servlet.GenericServlet");

    public static boolean isInEndpointBlacklist(String classname) {
        return ENDPOINT_BLACKLIST.contains(classname);
    }


    public static void judgeType(SimpleValue val, Type type, boolean isArray) {
        val.setArray(isArray || isArray(type));
        val.setInterface(isInterface(type));
        val.setConstant(isConstant(type));
        val.setConstantArray(isConstantArray(type));
        val.setCollection(isCollection(type));
        val.getType().add(type.toString());
    }

    public static SimpleObject[] extractObjectFromInvokeExpr(InvokeExpr invokeExpr, ValueContainer container) {
        int size = invokeExpr.getArgCount() + 1;
        Value base = null;
        if (invokeExpr instanceof InstanceInvokeExpr) {
            base = ((InstanceInvokeExpr) invokeExpr).getBase();
        }
        SimpleObject[] objects = new SimpleObject[size];

        if (base != null) {
            SimpleObject obj = container.getOrAdd(base, false);
            if (obj != null && container.isValueNull(obj)) {
                obj = null;
            }
            objects[0] = obj;
        } else {
            objects[0] = null;
        }

        for (int i = 0; i < invokeExpr.getArgCount(); i++) {
            SimpleObject obj = container.getOrAdd(invokeExpr.getArg(i), false);
            if (obj != null && container.isValueNull(obj)) {
                obj = null;
            }
            objects[i + 1] = obj;
        }

        return objects;
    }


    public static boolean checkField(SimpleObject field, String[] fieldName, Set<String> types, ValueContainer container) {

        if (types.contains("java.lang.Object")) {
            return true;
        }

        Map<String, Set<String>> fatherClasses = new HashMap<>();

        for (String valType : types) {
            SootClass cls = getSootClass(valType);
            if (cls != null) {
                if (cls.isInterface()) {
                    return true;
                }
                String fieldType = getFieldType(cls, fieldName);
                if (fieldType == null && cls.isAbstract()) {
                    return true;
                } else if ("true".equals(fieldType)) {
                    return true;
                } else if (fieldType != null) {
                    Set<String> currentFieldTypes = container.getTypes(field);

                    if (currentFieldTypes.contains(fieldType)) {
                        return true;
                    } else { // fieldType 范围大于 currentFieldTypes 所以 检查 currentFieldTypes 是否继承自fieldType
                        for (String ft : currentFieldTypes) {
                            if (ft == null) continue;

                            if ("java.lang.Object".equals(ft)) return true;

                            if (fatherClasses.containsKey(ft)) {
                                if (fatherClasses.get(ft).contains(fieldType)) {
                                    return true;
                                }
                            } else {
                                SootClass sc = getSootClass(ft);
                                if (sc == null) continue;
                                Set<String> nodes = getAllFatherNodes(sc, true);
                                fatherClasses.put(ft, nodes);
                                if (nodes.contains(fieldType)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }

        }
        return false;
    }


    public static List<Set<Integer>> extractPositionsFromInvokeExpr(InvokeExpr invokeExpr, ValueContainer container) {
        List<Set<Integer>> positions = new ArrayList<>();
        if(container != null){
            if (invokeExpr instanceof InstanceInvokeExpr) {
                Value base = ((InstanceInvokeExpr) invokeExpr).getBase();
                SimpleObject obj = container.getObject(base);
                positions.add(container.getPositions(obj, true));
            } else {
                positions.add(Collections.singleton(PositionUtils.NOT_POLLUTED_POSITION));
            }
            // get args obj
            for (Value value : invokeExpr.getArgs()) {
                SimpleObject obj = container.getObject(value);
                positions.add(container.getPositions(obj, true));
            }
        }
        return positions;
    }

    public static SimpleObject extractObjectFromInvokeExpr(InvokeExpr invokeExpr, int index, ValueContainer container) {
        Value base = null;
        SimpleObject retObj = null;

        if(index == -1){
            if (invokeExpr instanceof InstanceInvokeExpr) {
                base = ((InstanceInvokeExpr) invokeExpr).getBase();
            }
            if(base != null){
                retObj = container.getObject(base);

            }
        }else if(index < invokeExpr.getArgCount()){
            base = invokeExpr.getArg(index);
            retObj = container.getObject(base);
        }

        if (retObj != null && container.isValueNull(retObj)) {
            retObj = null;
        }

        return retObj;
    }

}
