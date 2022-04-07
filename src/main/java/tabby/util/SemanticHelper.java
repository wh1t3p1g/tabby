package tabby.util;

import com.google.common.hash.Hashing;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JimpleLocalBox;
import tabby.dal.caching.bean.ref.MethodReference;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author wh1t3p1g
 * @since 2022/1/7
 */
public class SemanticHelper {

    private static List<String> ARRAY_TYPES
            = Arrays.asList(
                    "java.util.List",
                    "java.util.ArrayList",
                    "java.util.Set",
                    "java.util.HashTable",
                    "java.util.HashSet",
                    "java.util.Map",
                    "java.util.HashMap"
    ); // TODO 优化collection的污点追踪问题

    /**
     * 提取value的name
     * @param value
     * @return
     */
    public static String extractValueName(Object value){
        String name = value.toString();
        if(value instanceof Local){
            name = ((Local) value).getName();
        }else if(value instanceof InstanceFieldRef){
            SootField field = ((InstanceFieldRef) value).getField();
            if(field != null){
                name = field.getSignature();
            }
        }else if(value instanceof SootField){
            name = ((SootField) value).getSignature();
        }else if(value instanceof SootClass){
            name = ((SootClass) value).getName();
        }else if(value instanceof ArrayRef){
            Value base = ((ArrayRef) value).getBase();
            name = extractValueName(base);
        }

        return name;
    }

    public static String calculateUuid(Object value){
        String name = extractValueName(value);
        return hashString(name);
    }

    public static String hashString(String name){
        return Hashing.md5()
                .hashString(name, StandardCharsets.UTF_8)
                .toString();
    }

    public static boolean isInterface(Type type){
        if(type instanceof RefType){
            SootClass cls = ((RefType) type).getSootClass();
            return Modifier.isInterface(cls.getModifiers());
        }
        return false;
    }

    public static boolean isConstant(Type type){
        if(type instanceof PrimType){
            return true;
        }else if(type instanceof NullType){
            return true;
        }else {
            return "java.lang.String".equals(type.toString());
        }
    }

    public static boolean isNotPrimType(Value value){
        Type type = null;
        if(value instanceof Local){
            type = value.getType();
        }else if(value instanceof FieldRef){
            SootField field = ((FieldRef) value).getField();
            type = field.getType();
        }else if(value instanceof Constant){
            type = value.getType();
        }else if(value instanceof ArrayRef){
            Value base = ((ArrayRef) value).getBase();
            return isNotPrimType(base);
        }

        if(type != null){
            return !(type instanceof PrimType);
        }
        return true;
    }

    public static boolean isArray(Type type){
        return type instanceof ArrayType;
    }

    public static boolean isCollection(Type type){
        String typeName = type.toString();
        return ARRAY_TYPES.contains(typeName);
    }

    public static String getRealCallType(InvokeExpr ie, MethodReference targetMethodRef){
        String classname = "";
        List<ValueBox> valueBoxes = ie.getUseBoxes();
        for(ValueBox box:valueBoxes){
            if(box instanceof JimpleLocalBox){
                Value base = box.getValue();
                if(base != null){
                    classname = base.getType().toString();
                }
                break;
            }
        }

        if(classname.isEmpty()){
            classname = targetMethodRef.getClassname();
        }

        return classname;
    }

    public static String getInvokeType(InvokeExpr ie){
        String invokeType = "";
        if(ie instanceof StaticInvokeExpr){
            invokeType = "StaticInvoke";
        }else if(ie instanceof VirtualInvokeExpr){
            invokeType = "VirtualInvoke";
        }else if(ie instanceof SpecialInvokeExpr){
            invokeType = "SpecialInvoke";
        }else if(ie instanceof InterfaceInvokeExpr){
            invokeType = "InterfaceInvoke";
        }
        return invokeType;
    }

    /**
     * return a,a<f>b,null
     * @param value
     * @return
     */
    public static String getSimpleName(Value value){
        if(value instanceof InstanceFieldRef){
            Value base = ((InstanceFieldRef) value).getBase();
            String baseName = getSimpleName(base);
            SootField field = ((InstanceFieldRef) value).getField();
            return baseName+"<f>"+field.getName();
        }else if(value instanceof Local){
            return ((Local) value).getName();
        }else if(value instanceof ArrayRef){
            Value base = ((ArrayRef) value).getBase();
            return getSimpleName(base);
        }else if(value instanceof CastExpr){
            Value base = ((CastExpr) value).getOp();
            return getSimpleName(base);
        }else if(value instanceof StaticFieldRef || value instanceof Constant){
            return value.toString();
        }
//        else if(value instanceof Constant){
//            return null; // 不考虑常量
//        }
        else{
            System.out.println(value);
            return null;
        }
    }

    /**
     * return a,a[],a<f>b, null
     * @param value
     * @return
     */
    public static String getSimpleName0(Value value){
        if(value instanceof InstanceFieldRef){
            Value base = ((InstanceFieldRef) value).getBase();
            String baseName = getSimpleName(base);
            SootField field = ((InstanceFieldRef) value).getField();
            return baseName+"<f>"+field.getName();
        }else if(value instanceof Local){
            return ((Local) value).getName();
        }else if(value instanceof ArrayRef){
            Value base = ((ArrayRef) value).getBase();
            return getSimpleName(base) + "<a>";
        }else if(value instanceof CastExpr){
            Value base = ((CastExpr) value).getOp();
            return getSimpleName(base);
        }else if(value instanceof StaticFieldRef || value instanceof Constant){
            return value.toString();
        }
//        else if(value instanceof Constant){
//            return null; // 不考虑常量
//        }
        else{
            System.out.println(value);
            return null;
        }
    }

    public static boolean hasField(String cls, String fieldName){
        if(fieldName.startsWith("<f>")){
            fieldName = fieldName.substring(3);
        }
        fieldName = fieldName.replace("<a>", "");
        if(fieldName.contains("<f>")){
            String[] pos = fieldName.split("<f>");
            boolean flag = true;
            for(String p:pos){
                if(cls.endsWith("[]")){
                    cls = cls.substring(0, cls.length()-2);
                }
                SootField field = getField(cls, p);
                if(field == null){
                    flag = false;
                    break;
                }else{
                    cls = field.getType().toString();
                }
            }
            return flag;
        }else{
            SootField field = getField(cls, fieldName);
            return field != null;
        }
    }

    public static boolean hasField(SootClass cls, String fieldName){
        if(cls == null || fieldName == null) return false;
        SootField field = getField(cls, fieldName);
        return field != null;
    }

    public static boolean isExtendFrom(Set<String> sources, String dest){
        if(sources.contains(dest)) return true;

        for(String source:sources){
            SootClass cls = getSootClass(source);
            if(isExtendFrom(cls, dest)){
                return true;
            }
        }
        return false;
    }

    public static boolean isExtendFrom(SootClass source, String dest){

        if(source != null){
            if(dest.equals(source.getName())) return true;

            if(source.hasSuperclass()){
                return isExtendFrom(source.getSuperclass(), dest);
            }else if(source.getInterfaceCount() > 0){
                for(SootClass inface:source.getInterfaces()){
                    if(isExtendFrom(inface, dest)){
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static SootMethod getMethod(SootClass cls, String subSignature){
        try{
            return cls.getMethod(subSignature);
        }catch (RuntimeException e){
            SootMethod method = null;
            if(cls.hasSuperclass()){
                method = getMethod(cls.getSuperclass(), subSignature);
            }

            if(method == null && cls.getInterfaceCount() > 0){
                for(SootClass inface:cls.getInterfaces()){
                    method = getMethod(inface, subSignature);
                    if(method != null) break;
                }
            }
            return method;
        }
    }

    public static SootField getField(String cls, String fieldName){
        try{
            SootClass sc = Scene.v().getSootClass(cls);
            return getField(sc, fieldName);
        }catch (Exception ignore){

        }
        return null;
    }

    public static SootField getField(SootClass cls, String fieldName){
        if(cls == null) return null;
        try{
            return cls.getFieldByName(fieldName);
        }catch (Exception ignore){
            if(cls.hasSuperclass()){
                return getField(cls.getSuperclass(), fieldName);
            }
        }
        return null;
    }

    public static SootClass getSootClass(String cls){
        try{
            return Scene.v().getSootClass(cls);
        }catch (Exception ignore){
        }
        return null;
    }

    public static String replaceFirst(String old, String identify, String replace){
        if(old.contains(identify)){
            int start = old.indexOf(identify);
            int end = start + identify.length();
            return old.substring(0, start) + replace + old.substring(end);
        }
        return null;
    }

    public static String getFieldNameByMethodName(String methodName){
        String fieldName = null;
        if(methodName.startsWith("set") || methodName.startsWith("get")){
            fieldName = methodName.substring(3); // getXxx setXxx
        }else if(methodName.startsWith("is")){
            fieldName = methodName.substring(2); // isXxx
        }
        if(fieldName == null || fieldName.isEmpty()) return null;

        char firstChar = fieldName.charAt(0);
        if(Character.isLowerCase(firstChar)) return null; // check getS or gets

        firstChar = Character.toLowerCase(firstChar);
        String appendString = fieldName.substring(1);

        return firstChar+appendString;
    }

    public static boolean hasDefaultConstructor(SootClass cls){
        if(cls == null) return false;

        try{
            SootMethod method = cls.getMethod("void <init>()");
            return method != null;
        }catch (Exception e){
            return false;
        }
    }
}
