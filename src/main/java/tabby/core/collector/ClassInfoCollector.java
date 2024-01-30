package tabby.core.collector;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import soot.SootClass;
import soot.SootMethod;
import soot.tagkit.AnnotationTag;
import soot.tagkit.Tag;
import soot.tagkit.VisibilityAnnotationTag;
import tabby.common.bean.edge.Has;
import tabby.common.bean.ref.ClassReference;
import tabby.common.bean.ref.MethodReference;
import tabby.core.container.DataContainer;
import tabby.core.container.RulesContainer;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * @author wh1t3p1g
 * @since 2021/8/31
 */
@Service
public class ClassInfoCollector {

    @Autowired
    private DataContainer dataContainer;

    @Async("tabby-collector")
    public CompletableFuture<ClassReference> collect(SootClass cls){
        return CompletableFuture.completedFuture(collect0(cls, dataContainer));
    }

    /**
     * 仅收集classRef，不保存到内存
     * @param cls
     * @param dataContainer
     * @return
     */
    public static ClassReference collect0(SootClass cls, DataContainer dataContainer){
        ClassReference classRef = ClassReference.newInstance(cls);
        Set<String> relatedClassnames = getAllFatherNodes(cls);
        classRef.setSerializable(relatedClassnames.contains("java.io.Serializable"));
        classRef.setStrutsAction(relatedClassnames.contains("com.opensymphony.xwork2.ActionSupport")
                || relatedClassnames.contains("com.opensymphony.xwork2.Action"));
        // 提取类函数信息
        if(cls.getMethodCount() > 0){
            for (SootMethod method : cls.getMethods()) {
                extractMethodInfo(method, classRef, relatedClassnames, dataContainer);
            }
        }
        return classRef;
    }


    /**
     * 提取函数基础信息，并保存到内存中
     * @param method
     * @param ref
     */
    public static void extractMethodInfo(SootMethod method,
                                         ClassReference ref,
                                         Set<String> relatedClassnames,
                                         DataContainer dataContainer
    ){
        RulesContainer rulesContainer = dataContainer.getRulesContainer();
        String classname = ref.getName();
        MethodReference methodRef = MethodReference.newInstance(classname, method);
        rulesContainer.applyRule(classname, methodRef, relatedClassnames);
        methodRef.setEndpoint(ref.isStrutsAction() || isEndpoint(method, relatedClassnames));
        methodRef.setNettyEndpoint(isNettyEndpoint(method, relatedClassnames));
        methodRef.setGetter(isGetter(method));
        methodRef.setSetter(isSetter(method));
        methodRef.setSerializable(relatedClassnames.contains("java.io.Serializable"));
        methodRef.setAbstract(method.isAbstract());
        methodRef.setHasDefaultConstructor(ref.isHasDefaultConstructor());
        methodRef.setFromAbstractClass(ref.isAbstract());

        Has has = Has.newInstance(ref, methodRef);
        ref.getHasEdge().add(has);
        dataContainer.store(has);
        dataContainer.store(methodRef);
    }


    /**
     * check method is an endpoint
     * @param method
     * @param relatedClassnames
     * @return
     */
    public static boolean isEndpoint(SootMethod method, Set<String> relatedClassnames){
        // check jsp _jspService
        if("_jspService".equals(method.getName())){
            return true;
        }

        // check from annotation
        List<Tag> tags = method.getTags();
        for (Tag tag : tags) {
            if (tag instanceof VisibilityAnnotationTag) {
                VisibilityAnnotationTag visibilityAnnotationTag = (VisibilityAnnotationTag) tag;
                for (AnnotationTag annotationTag : visibilityAnnotationTag.getAnnotations()) {
                    String type = annotationTag.getType();
                    if(type.endsWith("Mapping;")
                            || type.endsWith("javax/ws/rs/Path;")
                            || type.endsWith("javax/ws/rs/GET;")
                            || type.endsWith("javax/ws/rs/PUT;")
                            || type.endsWith("javax/ws/rs/DELETE;")
                            || type.endsWith("javax/ws/rs/POST;")){
                        return true;
                    }
                }
            }
        }

        // https://blog.csdn.net/melissa_heixiu/article/details/52472450
        List<String> requestTypes = new ArrayList<>(
                Arrays.asList("doGet","doPost","doPut","doDelete","doHead","doOptions","doTrace","service"));
        // check from servlet
        if((relatedClassnames.contains("javax.servlet.Servlet")
                || relatedClassnames.contains("javax.servlet.http.HttpServlet") // 防止依赖缺失情况下的识别
                || relatedClassnames.contains("javax.servlet.GenericServlet"))
                && requestTypes.contains(method.getName())){
            return true;
        }
        // not an endpoint
        return false;
    }

    public static boolean isNettyEndpoint(SootMethod method, Set<String> relatedClassnames){
        String classname = method.getDeclaringClass().getName();
        if("io.netty.channel.ChannelInboundHandler".equals(classname)
                || "io.netty.handler.codec.ByteToMessageDecoder".equals(classname)
        ){
            return false;
        }

        String methodName = method.getName();
        // check from ChannelInboundHandler
        List<String> nettyReadMethods = Arrays.asList("channelRead", "channelRead0", "messageReceived");
        if(relatedClassnames.contains("io.netty.channel.ChannelInboundHandler")
                && nettyReadMethods.contains(methodName)){
            return true;
        }

        // check from io.netty.handler.codec.ByteToMessageDecoder
        if(relatedClassnames.contains("io.netty.handler.codec.ByteToMessageDecoder")
                && "decode".equals(methodName)){
            return true;
        }
        // not an endpoint
        return false;
    }

    public static boolean isGetter(SootMethod method){
        String methodName = method.getName();
        String returnType = method.getReturnType().toString();
        boolean noParameter = method.getParameterCount() == 0;
        boolean isPublic = method.isPublic();

        if(!noParameter || !isPublic) return false;

        if(methodName.startsWith("get") && methodName.length() > 3){
            return !"void".equals(returnType);
        }else if(methodName.startsWith("is") && methodName.length() > 2){
            return "boolean".equals(returnType);
        }

        return false;
    }

    public static boolean isSetter(SootMethod method){
        String methodName = method.getName();
        String returnType = method.getReturnType().toString();
        boolean singleParameter = method.getParameterCount() == 1;
        boolean isPublic = method.isPublic();

        if(!isPublic || !singleParameter) return false;

        if(methodName.startsWith("set") && methodName.length() > 3){
            return "void".equals(returnType);
        }

        return false;
    }


    public static Set<String> getAllFatherNodes(SootClass cls){
        Set<String> nodes = new HashSet<>();
        if(cls.hasSuperclass() && !cls.getSuperclass().getName().equals("java.lang.Object")){
            nodes.add(cls.getSuperclass().getName());
            nodes.addAll(getAllFatherNodes(cls.getSuperclass()));
        }
        if(cls.getInterfaceCount() > 0){
            cls.getInterfaces().forEach(intface -> {
                nodes.add(intface.getName());
                nodes.addAll(getAllFatherNodes(intface));
            });
        }
        return nodes;
    }
}