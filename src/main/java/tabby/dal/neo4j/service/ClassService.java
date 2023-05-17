package tabby.dal.neo4j.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tabby.config.GlobalConfiguration;
import tabby.dal.neo4j.repository.ClassRefRepository;
import tabby.dal.neo4j.repository.MethodRefRepository;
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
//        classRefRepository.deleteAll();
//        methodRefRepository.deleteAll();
    }

    public void importClassRef(){
        if(GlobalConfiguration.IS_DOCKER_IMPORT_PATH){
            classRefRepository.loadClassRefFromCSV("/var/lib/neo4j/import/GRAPHDB_PUBLIC_CLASSES.csv");
        } else if (FileUtils.fileExists(GlobalConfiguration.CLASSES_OUTPUT_PATH)){
            classRefRepository.loadClassRefFromCSV(
                    FileUtils.getWinPath(GlobalConfiguration.CLASSES_OUTPUT_PATH));
        }
    }

    public void buildEdge(){
        if(GlobalConfiguration.IS_DOCKER_IMPORT_PATH){
            log.info("Save Extend relationship");
            classRefRepository.loadExtendEdgeFromCSV("/var/lib/neo4j/import/GRAPHDB_PUBLIC_EXTEND.csv");
        } else if(FileUtils.fileExists(GlobalConfiguration.EXTEND_RELATIONSHIP_OUTPUT_PATH)){
            log.info("Save Extend relationship");
            classRefRepository.loadExtendEdgeFromCSV(
                    FileUtils.getWinPath(GlobalConfiguration.EXTEND_RELATIONSHIP_OUTPUT_PATH));
        }

        if(GlobalConfiguration.IS_DOCKER_IMPORT_PATH){
            log.info("Save Interface relationship");
            classRefRepository.loadInterfacesEdgeFromCSV("/var/lib/neo4j/import/GRAPHDB_PUBLIC_INTERFACES.csv");
        } else if(FileUtils.fileExists(GlobalConfiguration.INTERFACE_RELATIONSHIP_OUTPUT_PATH)){
            log.info("Save Interface relationship");
            classRefRepository.loadInterfacesEdgeFromCSV(
                    FileUtils.getWinPath(GlobalConfiguration.INTERFACE_RELATIONSHIP_OUTPUT_PATH));
        }

        if(GlobalConfiguration.IS_DOCKER_IMPORT_PATH){
            log.info("Save Has relationship");
            classRefRepository.loadHasEdgeFromCSV("/var/lib/neo4j/import/GRAPHDB_PUBLIC_HAS.csv");
        } else if(FileUtils.fileExists(GlobalConfiguration.HAS_RELATIONSHIP_OUTPUT_PATH)){
            log.info("Save Has relationship");
            classRefRepository.loadHasEdgeFromCSV(
                    FileUtils.getWinPath(GlobalConfiguration.HAS_RELATIONSHIP_OUTPUT_PATH));
        }

        if(GlobalConfiguration.IS_DOCKER_IMPORT_PATH){
            log.info("Save Call relationship");
            methodRefRepository.loadCallEdgeFromCSV("/var/lib/neo4j/import/GRAPHDB_PUBLIC_CALL.csv");
        } else if(FileUtils.fileExists(GlobalConfiguration.CALL_RELATIONSHIP_OUTPUT_PATH)){
            log.info("Save Call relationship");
            methodRefRepository.loadCallEdgeFromCSV(
                    FileUtils.getWinPath(GlobalConfiguration.CALL_RELATIONSHIP_OUTPUT_PATH));
        }

        if(GlobalConfiguration.IS_DOCKER_IMPORT_PATH){
            log.info("Save Alias relationship");
            methodRefRepository.loadAliasEdgeFromCSV("/var/lib/neo4j/import/GRAPHDB_PUBLIC_ALIAS.csv");
        } else if(FileUtils.fileExists(GlobalConfiguration.ALIAS_RELATIONSHIP_OUTPUT_PATH)){
            log.info("Save Alias relationship");
            methodRefRepository.loadAliasEdgeFromCSV(
                    FileUtils.getWinPath(GlobalConfiguration.ALIAS_RELATIONSHIP_OUTPUT_PATH));
        }
    }

}
