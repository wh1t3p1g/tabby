package tabby.core.collector;

import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tabby.common.utils.FileUtils;
import tabby.config.GlobalConfiguration;
import tabby.plugin.jmod.JModTransferPlugin;
import tabby.plugin.jsp.JspCompilePlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author wh1t3p1g
 * @since 2022/12/18
 */
@Slf4j
@Getter
@Service
public class FileCollector {

    @Autowired
    public JModTransferPlugin jModTransferPlugin;

    private Set<String> xmlFiles = new HashSet<>();

    public Map<String, String> collect(String targetPath) {
        Map<String, String> allTargets = new HashMap<>();
        Path path = Paths.get(targetPath).toAbsolutePath();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Invalid target path: " + path);
        }

        try {
            // 创建 xml 临时目录
            FileLocation location = new FileLocation(path);
            Set<String> cps = location.resolve();
            Path xmlTempPath = null;

            if (GlobalConfiguration.IS_NEED_PROCESS_XML) {
                xmlTempPath = FileUtils.registerTempDirectory("xml_" + RandomStringUtils.randomAlphanumeric(3));
                xmlFiles.addAll(location.getXmlFiles());
            }

            for (String cp : cps) {
                Path temp = Paths.get(cp);
                if (Files.isDirectory(temp)) {
                    allTargets.put(cp, cp);
                } else {
                    String filename = temp.getFileName().toString();
                    String fileMd5 = FileUtils.getFileMD5(cp);

                    if (GlobalConfiguration.IS_WEB_MODE && GlobalConfiguration.rulesContainer.isInCommonJarList(filename)) {
                        GlobalConfiguration.libraries.put(fileMd5, cp);
                    } else {
                        allTargets.put(fileMd5, cp);

                        if (GlobalConfiguration.IS_NEED_PROCESS_XML && filename.endsWith(".jar")) {
                            // 从当前找到的所有jar中再次尝试提取xml文件
                            String jarPrefix = filename.substring(0, filename.length() - 4);
                            Path tmpDir = xmlTempPath.resolve(jarPrefix + RandomStringUtils.randomAlphanumeric(3));
                            FileUtils.extract(temp, tmpDir, Collections.singletonList(".xml"));
                        }
                    }
                }
            }
            if (GlobalConfiguration.IS_NEED_PROCESS_XML) {
                Map<String, Set<String>> targets = FileUtils.findAllTargets(xmlTempPath);
                xmlFiles.addAll(targets.get("xml"));
            }
        } catch (IOException e) {
//            e.printStackTrace();
            throw new IllegalArgumentException("Create temp directory error!");
        }

        return allTargets;
    }


    public Map<String, String> collectJdkDependencies() throws IOException {
        Map<String, String> dependencies = new HashMap<>();

        Set<String> jdkLibs = null;
        if (GlobalConfiguration.IS_USING_SETTING_JRE && FileUtils.fileExists(GlobalConfiguration.JRE_LIBS_PATH)) {
            jdkLibs = FileUtils.findAllJarFiles(GlobalConfiguration.JRE_LIBS_PATH, false);
        }
        if (jdkLibs == null || jdkLibs.isEmpty()) {
            jdkLibs = FileUtils.findAllJdkDependencies(jModTransferPlugin);
        }

        for (String filepath : jdkLibs) {
            if (GlobalConfiguration.IS_WITH_ALL_JDK) {
                dependencies.put(FileUtils.getFileMD5(filepath), filepath);
            } else if (GlobalConfiguration.IS_JRE9_MODULE) {
                if (filepath.contains("java.base.jmod")
                        || filepath.contains("java.desktop.jmod")
                        || filepath.contains("java.logging.jmod")) {
                    dependencies.put(FileUtils.getFileMD5(filepath), filepath);
                }
            } else {
                if (filepath.endsWith("rt.jar") || filepath.endsWith("jce.jar")) {
                    dependencies.put(FileUtils.getFileMD5(filepath), filepath);
                }
            }
        }

        return dependencies;
    }

    @Data
    public static class FileLocation {

        private Set<String> cps = new HashSet<>();
        private Set<String> xmlFiles = new HashSet<>();
        private boolean isJar = false;
        private boolean isFatJar = false;
        private boolean isWar = false;
        private boolean isClass = false;
        private boolean isJsp = false;
        private boolean isDir = false;
        private Path path;

        public FileLocation(Path path) {
            this.path = path;
            isDir = Files.isDirectory(path);
            if (!isDir) {
                String filepath = path.toString();
                if (filepath.endsWith(".jsp")) {
                    isJsp = true;
                } else if (filepath.endsWith(".class")) {
                    isClass = true;
                } else if (filepath.endsWith(".jar")) {
                    isFatJar = GlobalConfiguration.IS_CHECK_FAT_JAR
                            && FileUtils.isFatJar(filepath);
                    if (!isFatJar) {
                        isJar = true;
                    }
                } else if (filepath.endsWith(".war")) {
                    isWar = true;
                }
            }
        }

        public String getFileName() {
            return path.getFileName().toString();
        }

        public Set<String> resolve() {
            Path targetPath = null;
            if (isWar || isFatJar) {
                // unpack
                try {
                    targetPath = FileUtils.unpack(path, getFileName());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (isDir) {
                targetPath = path;
            }

            if (targetPath == null) {
                cps.add(path.toString());
            } else {
                try {
                    Map<String, Set<String>> targets = FileUtils.findAllTargets(targetPath);

                    cps.addAll(targets.get("jmods")); // jdk >= 9 直接添加
                    xmlFiles.addAll(targets.get("xml"));

                    if (isDir) {
                        // 对于目录类型，可能是最初的输入
                        // 需要对当前目录的jar和war做处理
                        Set<String> allUnpackedFiles = new HashSet<>();
                        allUnpackedFiles.addAll(targets.get("war"));
                        allUnpackedFiles.addAll(targets.get("jar"));
                        for (String unpacked : allUnpackedFiles) {
                            FileLocation location = new FileLocation(Paths.get(unpacked));
                            cps.addAll(location.resolve());
                            xmlFiles.addAll(location.getXmlFiles());
                        }
                    } else { // 一般不太可能 war里面嵌套一个war，所以这里暂时不考虑war
                        cps.addAll(targets.get("jar"));
                    }
                    // 处理class和jsp
                    Path tempPath = targetPath;
                    if (isDir) {
                        tempPath = FileUtils.registerTempDirectory(RandomStringUtils.randomAlphanumeric(3));
                    }
                    Set<String> remainedClasses = new HashSet<>();
                    Set<String> allClasses = targets.get("classes");
                    for (String cls : allClasses) {
                        if (cls.contains("BOOT-INF/classes/") || cls.contains("WEB-INF/classes/")) continue;
                        remainedClasses.add(cls);
                    }
                    if (remainedClasses.size() > 0) {
                        Path tmpClassesPath = tempPath.resolve("classes_" + RandomStringUtils.randomAlphanumeric(3));
                        FileUtils.copyAll(remainedClasses, tmpClassesPath, targetPath.toString());
                        cps.add(tmpClassesPath.toString());
                    }
                    // jsp
                    if (targets.get("jsp").size() > 0) {
                        Path tmpJspPath = tempPath.resolve("jsp_" + RandomStringUtils.randomAlphanumeric(3));
                        FileUtils.copyAll(targets.get("jsp"), tmpJspPath, targetPath.toString());
                        String allJarClasspath = String.join(File.pathSeparator, targets.get("jar"));
                        String jspClassFilepath = JspCompilePlugin.parse(tmpJspPath.toString(), allJarClasspath);
                        List<String> allPaths = Arrays.asList(jspClassFilepath, "org", "apache", "jsp");
                        String output = String.join(File.separator, allPaths);
                        if (FileUtils.fileExists(output)) {
                            cps.add(output);
                        }
                    }
                    //   BOOT-INF/classes
                    //   WEB-INF/classes
                    Path classes = targetPath.resolve("BOOT-INF/classes");
                    if (Files.exists(classes)) {
                        cps.add(classes.toString());
                    }
                    classes = targetPath.resolve("WEB-INF/classes");
                    if (Files.exists(classes)) {
                        cps.add(classes.toString());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return cps;
        }
    }

}
