package tabby.dal.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tabby.common.bean.ref.ClassReference;
import tabby.dal.repository.ClassRepository;
import tabby.config.GlobalConfiguration;

import java.util.List;


/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Slf4j
@Service
public class ClassRefService {

    @Autowired
    private ClassRepository classRepository;

    public ClassReference getClassRefByName(String name){
        return classRepository.findClassReferenceByName(name);
    }

    public void save(ClassReference ref){
        classRepository.save(ref);
    }

    public void save(Iterable<ClassReference> refs){
        classRepository.saveAll(refs);
    }

    public void save2Csv(){
        classRepository.save2Csv(GlobalConfiguration.CLASSES_OUTPUT_PATH);
    }

    public List<ClassReference> loadNecessaryClassRefs(){
        return classRepository.findAllNecessaryClassRefs();
    }

    public int countAll(){
        return classRepository.countAll();
    }

}
