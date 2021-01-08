package tabby.core.data;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.util.NumberedString;
import tabby.db.bean.node.*;
import tabby.db.bean.ref.ClassReference;
import tabby.db.bean.ref.MethodReference;
import tabby.db.service.ClassRefService;
import tabby.db.service.MethodRefService;
import tabby.db.service.RelationshipService;

import java.util.*;

/**
 * global data container
 * @author wh1t3P1g
 * @since 2021/1/7
 */
@Getter
@Setter
@Slf4j
@Component
public class DataContainer {

    @Autowired
    private ClassRefService classRefService;
    @Autowired
    private MethodRefService methodRefService;
    @Autowired
    private RelationshipService relationshipService;

    public static int MAX_NODE = 20000; // 最多临时存储2w个节点

    private Map<String, ClassReference> savedClassRefs = new HashMap<>();
    private Map<String, MethodReference> savedMethodRefs = new HashMap<>();

    private Set<HasNode> savedHasNodes = new HashSet<>();
    private Set<CallNode> savedCallNodes = new HashSet<>();
    private Set<AliasNode> savedAliasNodes = new HashSet<>();
    private Set<ExtendNode> savedExtendNodes = new HashSet<>();
    private Set<InterfacesNode> savedInterfacesNodes = new HashSet<>();

    /**
     * check size and save nodes
     */
    public void check(String type, boolean check){
        switch (type){
            case "class":
                if(!check || savedClassRefs.size() > MAX_NODE){
                    List<ClassReference> refs = new ArrayList<>(savedClassRefs.values());
                    classRefService.save2Mongodb(refs);
                    savedClassRefs.clear();
                }
                break;
            case "method":
                if(!check || savedMethodRefs.size() > MAX_NODE){
                    List<MethodReference> refs = new ArrayList<>(savedMethodRefs.values());
                    methodRefService.save2Mongodb(refs);
                    savedMethodRefs.clear();
                }
                break;
            case "has":
                if(!check || savedHasNodes.size() > MAX_NODE){
                    Set<HasNode> refs = new HashSet<>(savedHasNodes);
                    relationshipService.batchInsertHasNodes(refs);
                    savedHasNodes.clear();
                }
                break;
            case "call":
                if(!check || savedCallNodes.size() > MAX_NODE){
                    Set<CallNode> refs = new HashSet<>(savedCallNodes);
                    relationshipService.batchInsertCallNodes(refs);
                    savedCallNodes.clear();
                }
                break;
            case "extend":
                if(!check || savedExtendNodes.size() > MAX_NODE){
                    Set<ExtendNode> refs = new HashSet<>(savedExtendNodes);
                    relationshipService.batchInsertExtendNodes(refs);
                    savedExtendNodes.clear();
                }
                break;
            case "interfaces":
                if(!check || savedInterfacesNodes.size() > MAX_NODE){
                    Set<InterfacesNode> refs = new HashSet<>(savedInterfacesNodes);
                    relationshipService.batchInsertInterfaceNodes(refs);
                    savedInterfacesNodes.clear();
                }
                break;
            case "alias":
                if(!check || savedAliasNodes.size() > MAX_NODE){
                    Set<AliasNode> refs = new HashSet<>(savedAliasNodes);
                    relationshipService.batchInsertAliasNodes(refs);
                    savedAliasNodes.clear();
                }
                break;
        }
    }

    /**
     * store nodes
     * insert if node not exist
     * replace if node exist
     * @param ref node
     * @param <T> node type
     */
    public <T> void store(T ref, boolean store) {
        String type = null;
        if(ref instanceof ClassReference){
            ClassReference classRef = (ClassReference) ref;
            savedClassRefs.put(classRef.getName(), classRef);
            type = "class";
        }else if(ref instanceof MethodReference){
            MethodReference methodRef = (MethodReference) ref;
            savedMethodRefs.put(methodRef.getSignature(), methodRef);
            type = "method";
        }else if(ref instanceof HasNode){
            savedHasNodes.add((HasNode) ref);
            type = "has";
        }else if(ref instanceof CallNode){
            savedCallNodes.add((CallNode) ref);
            type = "call";
        }else if(ref instanceof InterfacesNode){
            savedInterfacesNodes.add((InterfacesNode) ref);
            type = "interfaces";
        }else if(ref instanceof ExtendNode){
            savedExtendNodes.add((ExtendNode) ref);
            type = "extend";
        }else if(ref instanceof AliasNode){
            savedAliasNodes.add((AliasNode) ref);
            type = "alias";
        }
        if(type != null && store){
            check(type, true); // 检查size
        }
    }

    public ClassReference getClassRefByName(String name){
        ClassReference ref = savedClassRefs.getOrDefault(name, null);
        if(ref != null) return ref;
        // find from mongodb
        ref = classRefService.getClassRefByName(name);
        return ref;
    }

    public MethodReference getMethodRefBySignature(String signature){
        MethodReference ref = savedMethodRefs.getOrDefault(signature, null);
        if(ref != null) return ref;
        // find from mongodb
        ref = methodRefService.getMethodRefBySignature(signature);
        return ref;
    }


    /**
     * 当前函数解决soot调用函数不准确的问题
     * soot的invoke表达式会将父类、接口等函数都归宿到当前类函数上，导致无法找到相应的methodRef
     * 解决这个问题，通过往父类、接口找相应的内容
     * 这里找到的是第一个找到的函数
     * @param sootMethodRef
     * @return
     */
    public MethodReference getMethodRefBySignature(SootMethodRef sootMethodRef){
        MethodReference target = getMethodRefBySignature(sootMethodRef.getSignature());
        if(target != null){
            return target;
        }

        return getMethodRefFromFatherNodes(sootMethodRef);
    }

    public MethodReference getMethodRefFromFatherNodes(SootMethodRef sootMethodRef){
        MethodReference target = null;
        SootClass cls = sootMethodRef.getDeclaringClass();
        SootClass tmpCls = cls;

        while(tmpCls.hasSuperclass()){
            SootClass superCls = tmpCls.getSuperclass();
            target = findMethodRef(superCls, sootMethodRef.getSubSignature());
            if(target != null){
                return target;
            }
            tmpCls = superCls;
        }
        if(cls.getInterfaceCount() > 0){
            return findMethodRefFromInterfaces(cls, sootMethodRef.getSubSignature());
        }
        return null;
    }

    private MethodReference findMethodRefFromInterfaces(SootClass cls, NumberedString subSignature){
        MethodReference target = null;
        for(SootClass interCls:cls.getInterfaces()){
            target = findMethodRef(interCls, subSignature);
            if(target != null){
                return target;
            }else if(interCls.getInterfaceCount() > 0){
                target = findMethodRefFromInterfaces(interCls, subSignature);
                if(target != null){
                    return target;
                }
            }
        }
        return null;
    }

    private MethodReference findMethodRef(SootClass cls, NumberedString subSignature){
        try{
            SootMethod targetMethod = cls.getMethod(subSignature);
            return getMethodRefBySignature(targetMethod.getSignature());
        }catch (RuntimeException e){
            // 当前类没找到函数，继续往父类找
        }
        return null;
    }

    public void save2Neo4j(){
        classRefService.clear(); // TODO 初始化图数据库 正式版去掉

        log.info("Load "+ savedMethodRefs.size()+ " method reference cache");
        methodRefService.importMethodRef();
        log.info("Load "+ savedClassRefs.size() +" class reference cache");
        classRefService.importClassRef();
        classRefService.buildEdge();
    }

}
