package tabby.neo4j.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.stereotype.Service;
import tabby.config.GlobalConfiguration;
import tabby.neo4j.bean.ref.MethodReference;
import tabby.neo4j.bean.ref.handle.MethodRefHandle;
import tabby.neo4j.repository.MethodRefRepository;
import tabby.util.FileUtils;

import java.util.List;
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

    public List<String> findAllByOutingCount(int outing, int skip, int limit){
        return methodRefRepository.findAllMethodRefByOutingCount(outing, skip, limit);
    }

    public int countAllByOutingCount(int outing){
        return methodRefRepository.countAllMethodRefByOutingCount(outing);
    }

    public List<String> findAllSinks(){
        return methodRefRepository.findAllSinks();
    }

    public List<String> findAllByInvokedMethodSignature(String signature){
        return methodRefRepository.findAllByInvokedMethodSignature(signature);
    }

    public MethodRefRepository getRepository(){
        return methodRefRepository;
    }
}
