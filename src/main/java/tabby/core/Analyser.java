package tabby.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.*;
import soot.options.Options;
import tabby.core.scanner.ClassInfoScanner;
import tabby.dal.cache.CacheHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Slf4j
@Component
public class Analyser {

    @Autowired
    private CacheHelper cacheHelper;
    @Autowired
    private ClassInfoScanner classInfoScanner;

    /**
     * 运行当前soot分析
     * 针对目标文件夹
     * @param target
     */
    public void runSootAnalysis(String[] target){
        if(target == null) return;
        String targetDirectory = String.join(File.separator, System.getProperty("user.dir"), String.join(File.separator,target));
        Options.v().set_process_dir(Collections.singletonList(targetDirectory)); // 设置待分析目录
        PhaseOptions.v().setPhaseOption("wjtp.classTransformer", "on");
        log.debug("Target directory: " + targetDirectory);
        log.info("Start soot analysis to " + targetDirectory);
        try{
            Main.v().autoSetOptions();
            Scene.v().loadNecessaryClasses();
            PackManager.v().runPacks();
            if (!Options.v().oaat()) {
                PackManager.v().writeOutput();
            }
        }catch (CompilationDeathException e){
            if (e.getStatus() != CompilationDeathException.COMPILATION_SUCCEEDED) {
                throw e;
            } else {
                return;
            }
        }
        log.info("Soot analysis done!");
    }


    public void runSootAnalysisWithJDK(){
        try{
            cacheHelper.loadRuntimeClasses(getJdkDependencies());
            PhaseOptions.v().setPhaseOption("wjtp.classTransformer", "off");
            Options.v().set_process_dir(getJdkDependencies());
            Main.v().autoSetOptions();
            Scene.v().loadNecessaryClasses();
            PackManager.v().runPacks();
//            if (!Options.v().oaat()) {
//                PackManager.v().writeOutput();
//            }
            classInfoScanner.collect();
            classInfoScanner.build();
            classInfoScanner.save();
        }catch (CompilationDeathException e){
            if (e.getStatus() != CompilationDeathException.COMPILATION_SUCCEEDED) {
                throw e;
            } else {
                return;
            }
        }
    }

    public List<String> getJdkDependencies(){
        List<String> jdk = new ArrayList<>(Arrays.asList(Scene.v().defaultClassPath().split(":")));
        jdk.add(jdk.get(0).replace("rt.jar","jsse.jar"));
        jdk.add(jdk.get(0).replace("rt.jar","charsets.jar"));
        jdk.add(jdk.get(0).replace("rt.jar","ext/sunec.jar"));
        return jdk;
    }
}
