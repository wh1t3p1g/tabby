package tabby.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.*;
import soot.options.Options;
import tabby.config.GlobalConfiguration;
import tabby.core.discover.xstream.SimpleXStreamGadgetDiscover;
import tabby.core.scanner.CallGraphScanner;
import tabby.core.scanner.ClassInfoScanner;
import tabby.neo4j.cache.CacheHelper;
import tabby.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

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
    @Autowired
    private SimpleXStreamGadgetDiscover discover;

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
            Set<String> targets = new HashSet<>(getJdkDependencies());
            if(!isOnlyJDK){
                targets.addAll(FileUtils.getTargetDirectoryJarFiles(path));
            }
            Scene.v().setSootClassPath(String.join(File.pathSeparator, targets));
            Options.v().set_process_dir(new ArrayList<>(targets));
            Main.v().autoSetOptions();
            Scene.v().loadNecessaryClasses();
            cacheHelper.loadRuntimeClasses(new ArrayList<>(targets), false);

            // 类信息抽取
            classInfoScanner.run(cacheHelper.getRuntimeClasses());
            // 函数调用分析
            PackManager.v().runPacks();
            callGraphScanner.run(new ArrayList<>(cacheHelper.getSavedMethodRefs().values()));
            classInfoScanner.save();
            clean(); // clean caches
//            discover.run();

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
        String javaHome = System.getProperty("java.home");
        String[] jre = new String[]{"lib/resources.jar","lib/rt.jar","lib/jsse.jar","lib/jce.jar","lib/charsets.jar","lib/ext/cldrdata.jar","lib/ext/dnsns.jar","lib/ext/jaccess.jar","lib/ext/localedata.jar","lib/ext/nashorn.jar","lib/ext/sunec.jar","lib/ext/sunjce_provider.jar","lib/ext/sunpkcs11.jar","lib/ext/zipfs.jar","lib/management-agent.jar"};

        List<String> exists = new ArrayList<>();
        for(String cp:jre){
            String path = String.join(File.separator, javaHome, cp);
            if(FileUtils.fileExists(path)){
                exists.add(path);
            }
        }
        log.info("Get " +exists.size()+" jre jars, supposed to be 15.");
        return exists;
    }

    private void setClassPath(List<String> targets) throws IOException {
        List<String> exists = getJdkDependencies();
        log.info("Get " +exists.size()+" jre jars, supposed to be 15.");
        if(targets != null){
            exists.addAll(targets);
        }
        Scene.v().setSootClassPath(String.join(File.pathSeparator, exists));
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
