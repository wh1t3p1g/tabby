package tabby.core.discover.xstream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tabby.core.discover.BackForwardedDiscover;
import tabby.neo4j.bean.ref.MethodReference;

import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/11/21
 */
@SuppressWarnings({"unchecked"})
@Slf4j
@Component
public class SimpleXStreamGadgetDiscover extends BackForwardedDiscover {

    @Override
    public boolean constraint(MethodReference target) {
        return false; // 这里默认没限制
    }

    @Override
    public List<String> spread(MethodReference target) {
        Set<String> nextTargets = new HashSet<>();
        Map<MethodReference, Set<String>> sourceMap = new HashMap<>();
        // 获取直接调用
        sourceMap.put(target, new HashSet<>(methodRefService.findAllByInvokedMethodSignature(target.getSignature())));

        // 获取alias调用
        // 存在情况，当前无直接调用关系，但是 这个函数存在alias关系边，由这个alias函数扩散直接调用关系
        List<String> alias = methodRefService.getRepository().findAllAliasMethods(target.getSignature());
        for(String as:alias){
            MethodReference aliasMethodRef = cacheHelper.loadMethodRef(as);
            sourceMap.put(aliasMethodRef, new HashSet<>(methodRefService.getRepository().findAllCallMethods(as, "InterfaceInvoke")));
        }

        // 对所有可能的调用做分析
        sourceMap.forEach((methodRef, sources) -> {
            for(String source:sources){
                MethodReference sourceMethodRef = cacheHelper.loadMethodRef(source);
                if(check(sourceMethodRef, methodRef)){
                    nextTargets.add(source);
                }
            }
        });

        return new ArrayList<>(nextTargets);
    }
}
