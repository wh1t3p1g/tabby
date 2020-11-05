package tabby.dal.service;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.stereotype.Service;
import tabby.dal.bean.ref.ClassReference;
import tabby.dal.repository.ClassRefRepository;
import tabby.dal.repository.MethodRefRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Slf4j
@Service
@CacheConfig(cacheNames = {"classRef"})
public class ClassRefService {

    @Autowired
    private ClassRefRepository classRefRepository;
    @Autowired
    private MethodRefRepository methodRefRepository;

//    @Transactional
    public void saveAll(Collection<ClassReference> classRefs){
        List<List<ClassReference>> lists = Lists.partition(new ArrayList<>(classRefs), 500);
        int time = 1;
        lists.forEach((realClassRefs) -> {
            log.debug(time*500+"");
            classRefRepository.saveAll(realClassRefs);
        });
    }

    public void save(ClassReference classRef){
        classRefRepository.save(classRef);
    }

    public void clear(){
        methodRefRepository.deleteAll();
        classRefRepository.deleteAll();
    }
}
