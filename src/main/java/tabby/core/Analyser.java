package tabby.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.CompilationDeathException;
import soot.Main;
import soot.PackManager;
import soot.Scene;
import soot.options.Options;
import tabby.config.GlobalConfiguration;
import tabby.core.data.DataContainer;
import tabby.core.data.RulesContainer;
import tabby.core.scanner.CallGraphScanner;
import tabby.core.scanner.ClassInfoScanner;
import tabby.util.ClassLoaderUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Slf4j
@Component
public class Analyser {

    @Autowired
    private DataContainer dataContainer;
    @Autowired
    private ClassInfoScanner classInfoScanner;
    @Autowired
    private CallGraphScanner callGraphScanner;
    @Autowired
    private RulesContainer rulesContainer;

    public void runSootAnalysis(Map<String, String> targets, List<String> classpaths){
        try{
            Scene.v().setSootClassPath(String.join(File.pathSeparator, classpaths));
            List<String> stuff = new ArrayList<>();
            List<String> newIgnore = new ArrayList<>();
            targets.forEach((filename, filepath) -> {
                if(!rulesContainer.isIgnore(filename)){
                    stuff.add(filepath);
                    newIgnore.add(filename);
                }
            });
            rulesContainer.getIgnored().addAll(newIgnore);
            Options.v().set_process_dir(stuff);
            Main.v().autoSetOptions();
            Scene.v().loadNecessaryClasses();
            List<String> runtimeClasses = ClassLoaderUtils.getAllClasses(stuff);

            // 类信息抽取
            classInfoScanner.run(runtimeClasses);
            // 函数调用分析
            log.info("Run soot packs!");
            PackManager.v().runPacks();
            callGraphScanner.run(dataContainer.getSavedMethodRefs().values());
//            clean(); // clean caches
//            if (!Options.v().oaat()) {
//                PackManager.v().writeOutput();
//            }
        }catch (CompilationDeathException e){
            if (e.getStatus() != CompilationDeathException.COMPILATION_SUCCEEDED) {
                throw e;
            }
        }
    }

    public void save(){
        dataContainer.save2CSV();
        dataContainer.save2Neo4j();
        clean();
    }

    public Map<String, String> getJdkDependencies(){
        String javaHome = System.getProperty("java.home");
        String[] jre = new String[]{"lib/resources.jar","lib/rt.jar","lib/jsse.jar","lib/jce.jar","lib/charsets.jar","lib/ext/cldrdata.jar","lib/ext/dnsns.jar","lib/ext/jaccess.jar","lib/ext/localedata.jar","lib/ext/nashorn.jar","lib/ext/sunec.jar","lib/ext/sunjce_provider.jar","lib/ext/sunpkcs11.jar","lib/ext/zipfs.jar","lib/management-agent.jar"};

        Map<String, String> exists = new HashMap<>();
        for(String cp:jre){
            String path = String.join(File.separator, javaHome, cp);
            File file = new File(path);
            if(file.exists()){
                exists.put(file.getName(), path);
            }
        }
        log.info("Get " +exists.size()+" jre jars, supposed to be 15.");
        return exists;
    }

    private void setClassPath(List<String> targets) throws IOException {
        List<String> exists = (List<String>) getJdkDependencies().values();
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
                    if(file.getName().endsWith(".csv")){
                        Files.deleteIfExists(file.toPath());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
