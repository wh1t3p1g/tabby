package tabby.core.discover;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tabby.core.data.GadgetChain;
import tabby.neo4j.bean.ref.MethodReference;
import tabby.neo4j.cache.CacheHelper;
import tabby.neo4j.service.MethodRefService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author wh1t3P1g
 * @since 2020/12/10
 */
@Component
public abstract class BackForwardedDiscover implements Discover{

    @Autowired
    public MethodRefService methodRefService;
    @Autowired
    public CacheHelper cacheHelper;

    public List<String> sinks;
    public List<String> sources;
    public List<GadgetChain> gadgetChains;

    @Override
    public void run() {
        gadgetChains = new ArrayList<>();
        getSinks();
        getSources();

        if(sinks != null && !sinks.isEmpty()){
            for(String sink:sinks){
                MethodReference methodRef = cacheHelper.loadMethodRef(sink);
                GadgetChain chainHead = new GadgetChain();
                chainHead.setMethod(sink);
                chainHead.setMethodRef(methodRef);
//                chainHead.setObj(String.join(",", methodRef.getRelatedPosition()));
                gadgetChains.add(chainHead);
            }
        }

        if(!gadgetChains.isEmpty()){
            for(GadgetChain head: gadgetChains){
//                if("<java.io.FileOutputStream: void <init>(java.lang.String)>".equals(head.getMethod())){
                    flowThrough(head);
//                }
            }
        }
    }

    @Override
    public void flowThrough(GadgetChain gadgetChain) {
        gadgetChain.print(null);
        next(gadgetChain);
        if(gadgetChain.hasNext()){
            for(GadgetChain nextNode: gadgetChain.getNext()){
                if(gadgetChain.isInRecursion(nextNode.getMethod())) continue;
                if(isSource(nextNode.getMethodRef().getSubSignature())){
                    nextNode.print(null);
                    continue;
                }
                flowThrough(nextNode);
            }
        }
    }

    @Override
    public void getSinks() {
        sinks = methodRefService.findAllSinks();
    }

    @Override
    public void next(GadgetChain gadgetChain) {
        Set<String> possibleMethods = new HashSet<>();
        Set<String> possibleCalledMethods = new HashSet<>();
        possibleMethods.add(gadgetChain.getMethod());
        if("InterfaceInvoke".equals(gadgetChain.getInvokerType())){
            possibleMethods.addAll(methodRefService.getRepository().findAllAliasMethods(gadgetChain.getMethod())); // 找到所有可能的通路
        }

        possibleMethods.forEach(possibleMethod -> {
            possibleCalledMethods.addAll(methodRefService.getRepository().findAllCall(possibleMethod));
        });

        System.out.println(gadgetChain.getMethod() + ":"+possibleCalledMethods.size());

        for(String method:possibleCalledMethods){
            MethodReference methodRef = cacheHelper.loadMethodRef(method);
            if(methodRef == null) continue;

            GadgetChain chained = new GadgetChain(gadgetChain);
            chained.setMethod(method);
            chained.setMethodRef(methodRef);

            if(analysis(gadgetChain.getObj(), chained, methodRef, gadgetChain.getMethodRef())){
                gadgetChain.getNext().add(chained); // 这里添加上去的链，均是分析过后，认为可控的
            }
        }
    }

    @Override
    public boolean isSource(String method) {
        return sources != null && sources.contains(method);
    }
}
