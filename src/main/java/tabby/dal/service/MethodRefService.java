package tabby.dal.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.stereotype.Service;
import tabby.config.GlobalConfiguration;
import tabby.dal.bean.ref.MethodReference;
import tabby.dal.bean.ref.handle.MethodRefHandle;
import tabby.dal.repository.MethodRefRepository;
import tabby.util.FileUtils;

import java.util.Map;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Service
@CacheConfig(cacheNames = {"methodRef"})
public class MethodRefService {

    @Autowired
    private MethodRefRepository methodRefRepository;

    public void save(MethodReference methodReference){
        methodRefRepository.save(methodReference);
    }

    public void saveAll(Map<MethodRefHandle, MethodReference> methodRefs){
        methodRefRepository.saveAll(methodRefs.values());
    }

    public void importMethodRef(){
        if(FileUtils.fileExists(GlobalConfiguration.METHODS_CACHE_PATH)){
            methodRefRepository.loadMethodRefFromCSV(GlobalConfiguration.METHODS_CACHE_PATH);
        }
    }
}
