package tabby.dal.service;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.stereotype.Service;
import tabby.config.GlobalConfiguration;
import tabby.dal.bean.ref.ClassReference;
import tabby.dal.repository.ClassRefRepository;
import tabby.dal.repository.MethodRefRepository;
import tabby.util.FileUtils;

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

    public void importClassRef(){
        if(FileUtils.fileExists(GlobalConfiguration.CLASSES_CACHE_PATH)){
            classRefRepository.loadClassRefFromCSV(GlobalConfiguration.CLASSES_CACHE_PATH);
        }
    }

    public void buildEdge(){
        if(FileUtils.fileExists(GlobalConfiguration.EXTEND_RELATIONSHIP_CACHE_PATH)){
            classRefRepository.loadExtendEdgeFromCSV(GlobalConfiguration.EXTEND_RELATIONSHIP_CACHE_PATH);
        }
        if(FileUtils.fileExists(GlobalConfiguration.INTERFACE_RELATIONSHIP_CACHE_PATH)){
            classRefRepository.loadInterfacesEdgeFromCSV(GlobalConfiguration.INTERFACE_RELATIONSHIP_CACHE_PATH);
        }
        if(FileUtils.fileExists(GlobalConfiguration.HAS_RELATIONSHIP_CACHE_PATH)){
            classRefRepository.loadHasEdgeFromCSV(GlobalConfiguration.HAS_RELATIONSHIP_CACHE_PATH);
        }
        if(FileUtils.fileExists(GlobalConfiguration.CALL_RELATIONSHIP_CACHE_PATH)){
            methodRefRepository.loadCallEdgeFromCSV(GlobalConfiguration.CALL_RELATIONSHIP_CACHE_PATH);
        }
    }
}
