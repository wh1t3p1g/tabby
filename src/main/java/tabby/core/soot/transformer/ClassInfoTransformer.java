package tabby.core.soot.transformer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.SceneTransformer;
import soot.SootClass;
import tabby.dal.cache.CacheHelper;
import tabby.util.ClassResourceEnumerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author wh1t3P1g
 * @since 2020/11/3
 */
@Slf4j
@Component
public class ClassInfoTransformer extends SceneTransformer {

    @Autowired
    private CacheHelper cacheHelper;

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        log.info(phaseName);
        ClassResourceEnumerator classResourceEnumerator = null;
        List<SootClass> classes = new ArrayList<>();

//        Chain<SootClass> classes = Scene.v().getApplicationClasses();
//        for(SootClass cls:classes){// 获取所有class
//            log.info(cls.getName());
//        }
        log.info(classes.size()+"");
    }
}
