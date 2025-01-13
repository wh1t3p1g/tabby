package tabby.dal.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tabby.common.bean.ref.ClassReference;
import tabby.dal.repository.ClassRepository;

import java.util.concurrent.CompletableFuture;


/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Slf4j
@Service
public class ClassRefService {

    @Autowired
    private ClassRepository classRepository;

    public ClassReference getClassRefByName(String name) {
        return classRepository.findClassReferenceByName(name);
    }

    public void save(ClassReference ref) {
        classRepository.save(ref);
    }

    @Async("tabby-saver")
    public CompletableFuture<Boolean> save(Iterable<ClassReference> refs) {
        classRepository.saveAll(refs);
        return CompletableFuture.completedFuture(true);
    }

    public void save2Csv(String filepath) {
        classRepository.save2Csv(filepath);
    }

    public int countAll() {
        return classRepository.countAll();
    }

}
