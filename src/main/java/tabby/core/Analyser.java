package tabby.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.*;
import soot.options.Options;
import tabby.config.GlobalConfiguration;
import tabby.core.scanner.CallGraphScanner;
import tabby.core.scanner.ClassInfoScanner;
import tabby.neo4j.cache.CacheHelper;
import tabby.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
    @Autowired
    private CallGraphScanner callGraphScanner;

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

    public void runSootAnalysis(String path, boolean isOnlyJDK){
        try{
            cacheHelper.clear("all");
            if(isOnlyJDK){
                Options.v().set_process_dir(getJdkDependencies());
                cacheHelper.loadRuntimeClasses(getJdkDependencies(), true);
            }else{
                List<String> targets = FileUtils.getTargetDirectoryJarFiles(path);
                Options.v().set_soot_classpath(String.join(File.pathSeparator, getJdkDependencies()));
//                targets.addAll(getJdkDependencies());
                Options.v().set_process_dir(targets);
                cacheHelper.loadRuntimeClasses(targets, false);
            }

            Main.v().autoSetOptions();
            Scene.v().loadNecessaryClasses();

            // 类信息抽取
            classInfoScanner.run(cacheHelper.getRuntimeClasses());
            // 函数调用分析
            PackManager.v().runPacks();
            callGraphScanner.run(new ArrayList<>(cacheHelper.getSavedMethodRefs().values()));
            classInfoScanner.save();
            clean(); // clean caches

//            System.out.println(sortedMethodRefs.size());
//            if (!Options.v().oaat()) {
//                PackManager.v().writeOutput();
//            }
        }catch (CompilationDeathException e){
            if (e.getStatus() != CompilationDeathException.COMPILATION_SUCCEEDED) {
                throw e;
            } else {
                return;
            }
        }catch (IOException e){

        }
    }

    public List<String> getJdkDependencies(){
        List<String> jdk = new ArrayList<>(Arrays.asList(Scene.v().defaultClassPath().split(":")));
        jdk.add(jdk.get(0).replace("rt.jar","jsse.jar"));
        jdk.add(jdk.get(0).replace("rt.jar","management-agent.jar"));
        jdk.add(jdk.get(0).replace("rt.jar","charsets.jar"));
        jdk.add(jdk.get(0).replace("rt.jar","ext/sunec.jar"));
        jdk.add(jdk.get(0).replace("rt.jar","ext/zipfs.jar"));
        jdk.add(jdk.get(0).replace("rt.jar","ext/cldrdata.jar"));
        jdk.add(jdk.get(0).replace("rt.jar","ext/dnsns.jar"));
        jdk.add(jdk.get(0).replace("rt.jar","ext/jaccess.jar"));
        jdk.add(jdk.get(0).replace("rt.jar","ext/localedata.jar"));
        jdk.add(jdk.get(0).replace("rt.jar","ext/nashorn.jar"));
        jdk.add(jdk.get(0).replace("rt.jar","ext/sunjce_provider.jar"));
        jdk.add(jdk.get(0).replace("rt.jar","ext/sunpkcs11.jar"));
        return jdk;
    }

    public void clean(){
        try {
            File cacheDir = new File(GlobalConfiguration.CACHE_PATH);
            File[] files = cacheDir.listFiles();
            if(files != null){
                for(File file: files){
                    Files.deleteIfExists(file.toPath());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
