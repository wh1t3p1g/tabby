package tabby.core.collector;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tabby.analysis.data.FileLocation;
import tabby.common.utils.FileUtils;
import tabby.config.GlobalConfiguration;
import tabby.plugin.jmod.JModTransferPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author wh1t3p1g
 * @since 2022/12/18
 */
@Service
public class FileCollector {

    @Autowired
    public JModTransferPlugin jModTransferPlugin;

    public Map<String, String> collect(String targetPath){
        Map<String, String> allTargets = new HashMap<>();
        Path path = Paths.get(targetPath).toAbsolutePath();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Invalid target path: " + path);
        }
        FileLocation location = new FileLocation(path);
        Set<String> cps = location.resolve();
        for(String cp:cps){
            Path temp = Paths.get(cp);
            if(Files.isDirectory(temp)){
                allTargets.put(cp, cp);
            }else{
                String filename = temp.getFileName().toString();
                String fileMd5 = FileUtils.getFileMD5(cp);
                if(GlobalConfiguration.IS_WEB_MODE && GlobalConfiguration.rulesContainer.isInCommonJarList(filename)){
                    GlobalConfiguration.libraries.put(fileMd5, cp);
                }else{
                    allTargets.put(fileMd5, cp);
                }
            }
        }
        return allTargets;
    }



    public Map<String, String> collectJdkDependencies() throws IOException {
        Map<String, String> dependencies = new HashMap<>();

        Set<String> jdkLibs = null;
        if(FileUtils.fileExists(GlobalConfiguration.JRE_LIBS_PATH)){
            jdkLibs = FileUtils.findAllJarFiles(GlobalConfiguration.JRE_LIBS_PATH, false);
        }
        if(jdkLibs == null || jdkLibs.isEmpty()){
            jdkLibs = FileUtils.findAllJdkDependencies(jModTransferPlugin);
        }

        for(String filepath:jdkLibs){
            if(GlobalConfiguration.IS_WITH_ALL_JDK){
                dependencies.put(FileUtils.getFileMD5(filepath), filepath);
            }else if(GlobalConfiguration.IS_JRE9_MODULE){
                if(filepath.endsWith("java.base.jmod.jar")
                        || filepath.endsWith("java.desktop.jmod.jar")
                        || filepath.endsWith("java.logging.jmod.jar")){
                    dependencies.put(FileUtils.getFileMD5(filepath), filepath);
                }
            }else{
                if(filepath.endsWith("rt.jar") || filepath.endsWith("jce.jar")){
                    dependencies.put(FileUtils.getFileMD5(filepath), filepath);
                }
            }
        }

        return dependencies;
    }

}
