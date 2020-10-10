package tabby.core;

import lombok.extern.slf4j.Slf4j;
import soot.CompilationDeathException;
import soot.Main;
import soot.PackManager;
import soot.Scene;
import soot.options.Options;

import java.io.File;
import java.util.Collections;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Slf4j
public class Analyser {

    /**
     * 运行当前soot分析
     * @param target
     */
    public static void runSootAnalysis(String[] target){
        if(target == null) return;
        String targetDirectory = String.join(File.separator, System.getProperty("user.dir"), String.join(File.separator,target));
        Options.v().set_process_dir(Collections.singletonList(targetDirectory)); // 设置待分析目录
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

}
