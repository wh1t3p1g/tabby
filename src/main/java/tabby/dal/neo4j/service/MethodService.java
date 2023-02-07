package tabby.dal.neo4j.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tabby.config.GlobalConfiguration;
import tabby.dal.neo4j.repository.MethodRefRepository;
import tabby.util.FileUtils;

/**
 * @author wh1t3P1g
 * @since 2021/3/29
 */
@Slf4j
@Service
public class MethodService {

    @Autowired
    private MethodRefRepository methodRefRepository;

    public void importMethodRef(){
        if(GlobalConfiguration.IS_DOCKER_IMPORT_PATH){
            methodRefRepository.loadMethodRefFromCSV("/var/lib/neo4j/import/GRAPHDB_PUBLIC_METHODS.csv");
        } else if(FileUtils.fileExists(GlobalConfiguration.METHODS_CACHE_PATH)){
            methodRefRepository.loadMethodRefFromCSV(
                    FileUtils.getWinPath(GlobalConfiguration.METHODS_CACHE_PATH));
        }
    }

    public MethodRefRepository getRepository(){
        return methodRefRepository;
    }
}
