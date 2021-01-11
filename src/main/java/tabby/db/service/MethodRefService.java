package tabby.db.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import tabby.config.GlobalConfiguration;
import tabby.db.bean.ref.MethodReference;
import tabby.db.repository.h2.MethodRepository;
import tabby.db.repository.neo4j.MethodRefRepository;
import tabby.util.FileUtils;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Slf4j
@Service
public class MethodRefService {

    @Autowired
    private MethodRefRepository methodRefRepository;
    @Autowired
    private MethodRepository methodRepository;


    public void importMethodRef(){
        if(FileUtils.fileExists(GlobalConfiguration.METHODS_CACHE_PATH)){
            methodRefRepository.loadMethodRefFromCSV(GlobalConfiguration.METHODS_CACHE_PATH);
        }
    }

    public MethodRefRepository getRepository(){
        return methodRefRepository;
    }

    @Cacheable("methods")
    public MethodReference getMethodRefBySignature(String signature){
        return methodRepository.findMethodReferenceBySignature(signature);
    }

    @CacheEvict(value = "methods", allEntries = true)
    public void clearCache(){
        log.info("All methods cache cleared!");
    }

    public void save(MethodReference ref){
        methodRepository.save(ref);
    }
    public void save(Iterable<MethodReference> refs){
        methodRepository.saveAll(refs);
    }

    public void save2Csv(){
        methodRepository.save2Csv(GlobalConfiguration.METHODS_CACHE_PATH);
    }
}
