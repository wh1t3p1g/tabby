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
import tabby.db.bean.edge.*;
import tabby.db.bean.ref.ClassReference;
import tabby.db.bean.ref.MethodReference;
import tabby.db.service.ClassRefService;
import tabby.db.service.MethodRefService;
import tabby.db.service.RelationshipsService;

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
    private RulesContainer rulesContainer;

    @Autowired
    private ClassRefService classRefService;
    @Autowired
    private MethodRefService methodRefService;
    @Autowired
    private RelationshipsService relationshipsService;

    private Map<String, ClassReference> savedClassRefs = new HashMap<>();
    private Map<String, MethodReference> savedMethodRefs = new HashMap<>();

    private Set<Has> savedHasNodes = new HashSet<>();
    private Set<Call> savedCallNodes = new HashSet<>();
    private Set<Alias> savedAliasNodes = new HashSet<>();
    private Set<Extend> savedExtendNodes = new HashSet<>();
    private Set<Interfaces> savedInterfacesNodes = new HashSet<>();

    /**
     * check size and save nodes
     */
    public void save(String type){
        switch (type){
            case "class":
                if(!savedClassRefs.isEmpty()){
                    classRefService.save(savedClassRefs.values());
                    savedClassRefs.clear();
                }
                break;
            case "method":
                if(!savedMethodRefs.isEmpty()){
                    methodRefService.save(savedMethodRefs.values());
                    savedMethodRefs.clear();
                }
                break;
            case "has":
                if(!savedHasNodes.isEmpty()){
                    relationshipsService.saveAllHasEdges(savedHasNodes);
                    savedHasNodes.clear();
                }
                break;
            case "call":
                if(!savedCallNodes.isEmpty()){
                    relationshipsService.saveAllCallEdges(savedCallNodes);
                    savedCallNodes.clear();
                }
                break;
            case "extend":
                if(!savedExtendNodes.isEmpty()){
                    relationshipsService.saveAllExtendEdges(savedExtendNodes);
                    savedExtendNodes.clear();
                }
                break;
            case "interfaces":
                if(!savedInterfacesNodes.isEmpty()){
                    relationshipsService.saveAllInterfacesEdges(savedInterfacesNodes);
                    savedInterfacesNodes.clear();
                }
                break;
            case "alias":
                if(!savedAliasNodes.isEmpty()){
                    relationshipsService.saveAllAliasEdges(savedAliasNodes);
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
    public <T> void store(T ref) {
        if(ref instanceof ClassReference){
            ClassReference classRef = (ClassReference) ref;
            savedClassRefs.put(classRef.getName(), classRef);
        }else if(ref instanceof MethodReference){
            MethodReference methodRef = (MethodReference) ref;
            savedMethodRefs.put(methodRef.getSignature(), methodRef);
        }else if(ref instanceof Has){
            savedHasNodes.add((Has) ref);
        }else if(ref instanceof Call){
            savedCallNodes.add((Call) ref);
        }else if(ref instanceof Interfaces){
            savedInterfacesNodes.add((Interfaces) ref);
        }else if(ref instanceof Extend){
            savedExtendNodes.add((Extend) ref);
        }else if(ref instanceof Alias){
            savedAliasNodes.add((Alias) ref);
        }
    }

    public ClassReference getClassRefByName(String name){
        ClassReference ref = savedClassRefs.getOrDefault(name, null);
        if(ref != null) return ref;
        // find from h2
        ref = classRefService.getClassRefByName(name);
        return ref;
    }

    public MethodReference getMethodRefBySignature(String classname, String function, String signature){
        MethodReference ref = savedMethodRefs.getOrDefault(signature, null);
        if(ref != null) return ref;
        // find from h2
        ref = methodRefService.getMethodRefBySignature(signature);
        // new from rules
        if(ref == null){
            TabbyRule.Rule rule = rulesContainer.getRule(classname, function);
            if(rule != null && rule.getSignatures().contains(signature)){
                ref = MethodReference.newInstance(function, signature);
                ref.setSink(rule.isSink());
                ref.setPolluted(rule.isSink());
                ref.setIgnore(rule.isIgnore());
                ref.setSource(rule.isSource());
                ref.setActions(rule.getActions());
                ref.setPollutedPosition(rule.getPolluted());
                ref.setInitialed(true);
                ref.setActionInitialed(true);
                store(ref);
            }
        }
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
        SootClass cls = sootMethodRef.getDeclaringClass();
        MethodReference target = getMethodRefBySignature(cls.getName(), sootMethodRef.getName(), sootMethodRef.getSignature());
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
            if(target == null){
                target = findMethodRef(superCls, sootMethodRef);
            }
            if(target != null){
                return target;
            }
            tmpCls = superCls;
        }
        if(cls.getInterfaceCount() > 0){
            return findMethodRefFromInterfaces(cls, sootMethodRef);
        }
        return null;
    }

    private MethodReference findMethodRefFromInterfaces(SootClass cls, SootMethodRef sootMethodRef){
        MethodReference target = null;
        for(SootClass interCls:cls.getInterfaces()){
            target = findMethodRef(interCls, sootMethodRef.getSubSignature());
            if(target == null){// try to get method using method name
                target = findMethodRef(interCls, sootMethodRef);
            }
            if(target != null){
                return target;
            }else if(interCls.getInterfaceCount() > 0){
                target = findMethodRefFromInterfaces(interCls, sootMethodRef);
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
            return getMethodRefBySignature(cls.getName(), targetMethod.getName(), targetMethod.getSignature());
        }catch (RuntimeException e){
            // 当前类没找到函数，继续往父类找
        }
        return null;
    }

    private MethodReference findMethodRef(SootClass cls, SootMethodRef sootMethodRef){

        try{
            SootMethod method = cls.getMethodByName(sootMethodRef.getName());
            return getMethodRefBySignature(cls.getName(), method.getName(), method.getSignature());
        }catch (RuntimeException e){
            // 找到了多个名字为methodName的函数
            try{
                SootMethod target = sootMethodRef.resolve();
                for(SootMethod method:cls.getMethods()){// 对找到的第一个符合条件的函数进行返回
                    if(sootMethodRef.getName().equals(method.getName()) && target.getParameterCount() == method.getParameterCount()){
                        return getMethodRefBySignature(cls.getName(), method.getName(), method.getSignature());
                    }
                }
            }catch (Exception ignored){

            }

        }
        return null;
    }

    public void loadNecessaryMethodRefs(){
        List<MethodReference> refs = methodRefService.loadNecessaryMethodRefs();
        refs.forEach(ref ->{
            savedMethodRefs.put(ref.getSignature(), ref);
        });
    }

    public void loadNecessaryClassRefs(){
        List<ClassReference> refs = classRefService.loadNecessaryClassRefs();
        refs.forEach(ref ->{
            savedClassRefs.put(ref.getName(), ref);
        });
    }

    public void save2Neo4j(){
        log.info("Clean old data in Neo4j.");
        classRefService.clear();
        log.info("Save methods to Neo4j.");
        methodRefService.importMethodRef();
        log.info("Save classes to Neo4j.");
        classRefService.importClassRef();
        log.info("Save relation to Neo4j.");
        classRefService.buildEdge();
    }

    public void save2CSV(){
        log.info("Save cache to CSV.");
        classRefService.save2Csv();
        methodRefService.save2Csv();
        relationshipsService.save2CSV();
        log.info("Save cache to CSV. DONE!");
    }

}
