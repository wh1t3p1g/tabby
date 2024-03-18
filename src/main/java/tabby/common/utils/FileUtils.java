package tabby.common.utils;

import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import tabby.config.GlobalConfiguration;
import tabby.plugin.jmod.JModTransferPlugin;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author wh1t3P1g
 * @since 2020/10/22
 */
@Slf4j
public class FileUtils {

    public static Map<String, String> findAllJdkDependencies(String target, boolean isNeedRecursion) throws IOException {
        Map<String, String> paths = new HashMap<>();
        Path path = Paths.get(target).toRealPath();
        String realPath = path.toString();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Invalid jar path: " + path);
        }

        Files.walkFileTree(path, new SimpleFileVisitor<Path>(){
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                String file = path.toAbsolutePath().toString();

                if(file.endsWith("local_policy.jar") || file.endsWith("US_export_policy.jar")){
                    return FileVisitResult.CONTINUE;
                }

                if(file.endsWith(".jar") || file.endsWith(".jmod")){
                    String fileMd5 = FileUtils.getFileMD5(file);
                    paths.put(fileMd5, file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if(isNeedRecursion || realPath.equals(dir.toAbsolutePath().toString())){
                    return super.preVisitDirectory(dir, attrs);
                }else{
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }
        });

        return paths;
    }

    public static boolean isFatJar(String filepath){
        Path path = Paths.get(filepath);
        if(Files.exists(path)){
            try(JarFile jarFile = new JarFile(path.toFile())){
                return jarFile.getEntry("WEB-INF") != null
                        || jarFile.getEntry("BOOT-INF") != null
                        || jarFile.getEntry("lib") != null;
            }catch (Exception ignore){}
        }
        return false;
    }

    public static void findAllTargets(Path path, Map<String, Set<String>> map) throws IOException {
        if(path == null) return;
        Files.walkFileTree(path, new SimpleFileVisitor<Path>(){
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                String file = path.toAbsolutePath().toString();
                if(file.endsWith(".jar")){
                    map.get("jar").add(file);
                } else if(file.endsWith(".class")){
                    map.get("classes").add(file);
                }else if(file.endsWith(".jmod")){
                    map.get("jmods").add(file);
                } else if(file.endsWith(".war")){
                    map.get("war").add(file);
                } else if(file.endsWith(".jsp") || file.endsWith(".jspx") || file.endsWith(".tld")){
                    map.get("jsp").add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static Path unpack(Path path, String filename) throws IOException {
        if(Files.exists(path)){
            Path output = registerTempDirectory(filename + RandomStringUtils.randomAlphanumeric(3));
            extract(path, output);
            return output;
        }
        return null;
    }

    /**
     * 提取当前fatJar到临时目录
     * @param jarPath
     * @param tmpDir
     * @throws IOException
     */
    public static void extract(Path jarPath, Path tmpDir) throws IOException {
        try (ZipFile zipFile = new ZipFile(jarPath.toFile())) {
            Enumeration<? extends ZipEntry> iterator = zipFile.entries();
            ZipEntry zipEntry;
            while (iterator.hasMoreElements()) {
                zipEntry = iterator.nextElement();
                Path fullPath = tmpDir.resolve(zipEntry.getName());
                if (!zipEntry.isDirectory()
                        && (zipEntry.getName().endsWith(".class")
                        || zipEntry.getName().endsWith(".jar")
                        || zipEntry.getName().endsWith(".jsp")
                        || zipEntry.getName().endsWith(".jspx")
                        || zipEntry.getName().endsWith(".tld")
                        || zipEntry.getName().endsWith(".jmod")
                )) {
                    Path dirName = fullPath.getParent();
                    if (dirName == null) {
                        throw new IllegalStateException("Parent of item is outside temp directory.");
                    }
                    if (!Files.exists(dirName)) {
                        Files.createDirectories(dirName);
                    }

                    Files.copy(zipFile.getInputStream(zipEntry), fullPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    public static void copy(String source, Path target) throws IOException {
        Path dirPath = target.getParent();
        if(!Files.exists(dirPath)){
            Files.createDirectories(dirPath);
        }
        Files.copy(Paths.get(source), target, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void copyAll(Set<String> sources, Path target, String basePath) throws IOException {
        if(Files.notExists(target)){
            Files.createDirectories(target);
        }
        int len = basePath.length();
        for(String source:sources){
            if(source.startsWith(basePath)){
                String sub = source.substring(len);
                if(sub.startsWith("/")){
                    sub = sub.substring(1);
                }
                Path path = target.resolve(sub);
                copy(source, path);
            }
        }
    }

    public static boolean fileExists(String path){
        if(path == null) return false;

        File file = new File(path);
        return file.exists();
    }

    public static void delete(String filepath){
        File file = new File(filepath);
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * 获取json格式的文件，并解析出具体的对象
     * 如果文件不存在，则返回null
     * @param path json文件路径
     * @param type 需要还原的对象类型
     * @return 还原后的对象，或null
     */
    public static Object getJsonContent(String path, Class<?> type){
        File file = new File(path);
        if(!file.exists()) return null;
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            return GlobalConfiguration.GSON.fromJson(reader, type);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void putJsonContent(String path, Object data){
        File file = new File(path);
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            writer.write(GlobalConfiguration.GSON.toJson(data));
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void putRawContent(String path, Collection<String> data){
        File file = new File(path);
        try (FileWriter writer = new FileWriter(file)){
            for(String d:data){
                writer.write(d+"\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 注册新的临时目录
     * @param directory temp directory
     * @return temp directory path
     * @throws IOException
     */
    public static Path registerTempDirectory(String directory) throws IOException {

        final Path tmpDir = createTempDirectory("tabby_"+directory);
        // Delete the temp directory at shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                deleteDirectory(tmpDir);
            } catch (IOException e) {
                log.error("Error cleaning up temp directory " + tmpDir.toString(), e);
            }
        }));

        return tmpDir;
    }

    public static Path createTempDirectory(String directory){
        String filepath = String.join(File.separator, GlobalConfiguration.TEMP_PATH, directory + UUID.randomUUID());
        if(!fileExists(filepath)){
            createDirectory(filepath);
        }
        return Paths.get(filepath);
    }

    /**
     * Recursively delete the directory root and all its contents
     * @param root Root directory to be deleted
     */
    public static void deleteDirectory(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void createDirectory(String path){
        File file = new File(path);
        if(file.mkdirs()){
            log.info("Create directory {} success!", path);
        }
    }

    public static void createDirectory(Path path){
        File file = path.toFile();
        if(!file.exists() && file.mkdirs()){
            log.info("Create directory {} success!", path);
        }
    }

    public static String getWinPath(String path){
        if(JavaVersionUtils.isWin()){
            path = "/"+path.replace("\\", "/");
        }
        return path;
    }

    public static String getFileMD5(String filepath){
        return getFileMD5(new File(filepath));
    }

    public static String getFileMD5(File file){
        try {
            return com.google.common.io.Files.hash(file, Hashing.md5()).toString();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    public static String getRealPath(String filepath) throws IllegalArgumentException{
        try{
            Path path = Paths.get(filepath);
            return path.toRealPath().toString();
        }catch (Exception ig){
            throw new IllegalArgumentException("Cache Path error!");
        }
    }

    public static void copy(String source, String target) throws IOException {
        Files.copy(Paths.get(source), Paths.get(target), StandardCopyOption.REPLACE_EXISTING);
    }

    private static String getJavaHome(){
        String javaHome = System.getProperty("java.home");
        if(javaHome == null){
            javaHome = System.getenv("JAVA_HOME");
        }
        return javaHome;
    }

    public static Set<String> findAllJdkDependencies(JModTransferPlugin plugin){
        String javaHome;
        if(GlobalConfiguration.IS_USING_SETTING_JRE){
            javaHome = GlobalConfiguration.TARGET_JAVA_HOME;
        }else{
            javaHome = getJavaHome();
            GlobalConfiguration.IS_JRE9_MODULE = true; // 仅允许 17 运行，所以为 true
        }

        if(javaHome == null){
            throw new RuntimeException("JAVA_HOME not set!");
        }

        Set<String> targets = new HashSet<>();
        if(GlobalConfiguration.IS_JRE9_MODULE){
            targets.add(String.join(File.separator, Arrays.asList(javaHome, "jmods")));
        }else{
            targets.add(String.join(File.separator, Arrays.asList(javaHome, "lib")));
            targets.add(String.join(File.separator, Arrays.asList(javaHome, "jre", "lib")));
        }

        Set<CompletableFuture<Boolean>> futures = new HashSet<>();
        Set<String> libraries = new HashSet<>();
        for(String target:targets){
            try{
                Path path = Paths.get(target).toRealPath();
                String realPath = path.toString();
                Files.walkFileTree(path, new SimpleFileVisitor<Path>(){
                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                        String source = path.toAbsolutePath().toString();
                        String filename = path.getFileName().toString();
                        if(source.endsWith("local_policy.jar") || source.endsWith("US_export_policy.jar")){
                            return FileVisitResult.CONTINUE;
                        }

                        String dest;
                        if(GlobalConfiguration.IS_USING_SETTING_JRE){
                            if(source.endsWith(".jar")){
                                dest = String.join(File.separator, Arrays.asList(GlobalConfiguration.JRE_LIBS_PATH, filename));
                                futures.add(plugin.transfer(source, dest));
                                libraries.add(dest);
                            }else if(source.endsWith(".jmod")){
                                dest = String.join(File.separator, Arrays.asList(GlobalConfiguration.JRE_LIBS_PATH, filename+".jar"));
                                futures.add(plugin.transfer(source, dest));
                                libraries.add(dest);
                            }
                        }else{
                            if(source.endsWith(".jar") || source.endsWith(".jmod")){
                                libraries.add(source);
                            }
                        }

                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        if(!GlobalConfiguration.IS_JRE9_MODULE || realPath.equals(dir.toAbsolutePath().toString())){
                            return super.preVisitDirectory(dir, attrs);
                        }else{
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                });
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        // wait for finished
        for(CompletableFuture<Boolean> future:futures){
            try {
                future.get();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        return libraries;
    }

    public static Set<String> findAllJarFiles(String target, boolean isNeedRecursion) throws IOException {
        Set<String> paths = new HashSet<>();
        Path path = Paths.get(target).toRealPath();
        String realPath = path.toString();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Invalid target path: " + path);
        }

        Files.walkFileTree(path, new SimpleFileVisitor<Path>(){
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                String file = path.toAbsolutePath().toString();
                if(file.endsWith(".jar") || file.endsWith(".jmod")){
                    paths.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if(isNeedRecursion || realPath.equals(dir.toAbsolutePath().toString())){
                    return super.preVisitDirectory(dir, attrs);
                }else{
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }
        });

        return paths;
    }
}
