package tabby.neo4j.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tabby.config.GlobalConfiguration;
import tabby.neo4j.repository.ClassRefRepository;
import tabby.neo4j.repository.MethodRefRepository;
import tabby.util.FileUtils;

/**
 * @author wh1t3P1g
 * @since 2021/3/29
 */
@Slf4j
@Service
public class ClassService {


    @Autowired
    private ClassRefRepository classRefRepository;
    @Autowired
    private MethodRefRepository methodRefRepository;


    public void clear(){
        classRefRepository.clearAll();
    }

    public void importClassRef(){
        if(FileUtils.fileExists(GlobalConfiguration.CLASSES_CACHE_PATH)){
            classRefRepository.loadClassRefFromCSV(GlobalConfiguration.CLASSES_CACHE_PATH);
        }
    }

    public void buildEdge(){
        if(FileUtils.fileExists(GlobalConfiguration.EXTEND_RELATIONSHIP_CACHE_PATH)){
            log.info("Save Extend relationship");
            classRefRepository.loadExtendEdgeFromCSV(GlobalConfiguration.EXTEND_RELATIONSHIP_CACHE_PATH);
        }
        if(FileUtils.fileExists(GlobalConfiguration.INTERFACE_RELATIONSHIP_CACHE_PATH)){
            log.info("Save Interface relationship");
            classRefRepository.loadInterfacesEdgeFromCSV(GlobalConfiguration.INTERFACE_RELATIONSHIP_CACHE_PATH);
        }
        if(FileUtils.fileExists(GlobalConfiguration.HAS_RELATIONSHIP_CACHE_PATH)){
            log.info("Save Has relationship");
            classRefRepository.loadHasEdgeFromCSV(GlobalConfiguration.HAS_RELATIONSHIP_CACHE_PATH);
        }
        if(FileUtils.fileExists(GlobalConfiguration.CALL_RELATIONSHIP_CACHE_PATH)){
            log.info("Save Call relationship");
            methodRefRepository.loadCallEdgeFromCSV(GlobalConfiguration.CALL_RELATIONSHIP_CACHE_PATH);
        }
        if(FileUtils.fileExists(GlobalConfiguration.ALIAS_RELATIONSHIP_CACHE_PATH)){
            log.info("Save Alias relationship");
            methodRefRepository.loadAliasEdgeFromCSV(GlobalConfiguration.ALIAS_RELATIONSHIP_CACHE_PATH);
        }
    }

}
