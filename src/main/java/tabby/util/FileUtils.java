package tabby.util;

import lombok.extern.slf4j.Slf4j;
import tabby.config.GlobalConfiguration;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * @author wh1t3P1g
 * @since 2020/10/22
 */
@Slf4j
public class FileUtils {

    /**
     * 对于目录下的class文件，一定要保持好目录结构，即目标目录下就是后续的具体目录（target/org/xxx/xxx）
     * @param target
     * @return
     * @throws IOException
     */
    public static Map<String, String> getTargetDirectoryJarFiles(String target, boolean checkFatJar) throws IOException {
        Map<String, String> paths = new HashMap<>();
        Path path = Paths.get(target).toAbsolutePath();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Invalid jar path: " + path);
        }

        if(path.toFile().isFile()){
            String filename = path.toFile().getName();
            if(filename.endsWith(".class")){
                paths.put(filename, path.getParent().toString());
            }else if(filename.endsWith(".war")){
                paths.putAll(unpackWarOrJarFiles(path, filename, false));
            }else if(filename.endsWith(".jar")){
                if(checkFatJar){
                    paths.putAll(unpackWarOrJarFiles(path, filename, true));
                }else{
                    paths.put(filename, path.toAbsolutePath().toString());
                }
            }
        }else{
            Files.walkFileTree(path, new SimpleFileVisitor<Path>(){
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                    String filename = path.getFileName().toString();
                    if(filename.endsWith(".jar")){
                        if(checkFatJar){
                            paths.putAll(unpackWarOrJarFiles(path, filename, true));
                        }else{
                            paths.put(filename, path.toAbsolutePath().toString());
                        }
                    }else if(filename.endsWith(".class")){
                        paths.put(filename, target);
                        // 对于.class文件，直接添加原目录，soot会爬取当前目录下的class文件
                        // 这里会有一些冗余，不过后面用了set 也无所谓
                    } else if(filename.endsWith(".war")){
                        paths.putAll(unpackWarOrJarFiles(path, filename, false));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        return paths;
    }

    public static Map<String, String> unpackWarOrJarFiles(Path path, String filename, boolean isFatJar) throws IOException {
        Map<String, String> paths = new HashMap<>();
        Path tmpDir = registerTempDirectory(filename);
        String libDir = isFatJar?"BOOT-INF/lib":"WEB-INF/lib";
        String classesDir = String.join(File.separator, tmpDir.toString(), isFatJar?"BOOT-INF/classes":"WEB-INF/classes");
        extract(path, tmpDir);
        if(tmpDir.resolve(libDir).toFile().exists()){
            Files.walkFileTree(tmpDir.resolve(libDir), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String filename = file.getFileName().toString();
                    if(filename.endsWith(".jar")){
                        paths.put(filename, file.toAbsolutePath().toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        if(new File(classesDir).exists()){
            paths.put(filename, classesDir); // 单独添加classes目录
        }

        return paths;
    }

    public static void extract(Path jarPath, Path tmpDir) throws IOException {
        try (JarInputStream jarInputStream = new JarInputStream(Files.newInputStream(jarPath))) {
            JarEntry jarEntry;
            while ((jarEntry = jarInputStream.getNextJarEntry()) != null) {
                Path fullPath = tmpDir.resolve(jarEntry.getName());
                if (!jarEntry.isDirectory()) {
                    Path dirName = fullPath.getParent();
                    if (dirName == null) {
                        throw new IllegalStateException("Parent of item is outside temp directory.");
                    }
                    if (!Files.exists(dirName)) {
                        Files.createDirectories(dirName);
                    }
                    try (OutputStream outputStream = Files.newOutputStream(fullPath)) {
                        copy(jarInputStream, outputStream);
                    }
                }
            }
        }
    }

    /**
     * Copy inputStream to outputStream. Neither stream is closed by this method.
     */
    public static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        final byte[] buffer = new byte[4096];
        int n;
        while ((n = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, n);
        }
    }

    public static boolean fileExists(String path){
        File file = new File(path);
        return file.exists();
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

    /**
     * 注册新的临时目录
     * @param directory temp directory
     * @return temp directory path
     * @throws IOException
     */
    public static Path registerTempDirectory(String directory) throws IOException {

        final Path tmpDir = Files.createTempDirectory(directory);
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

    public static String getWinPath(String path){
        if(JavaVersion.isWin()){
            path = "/"+path.replace("\\", "/");
        }
        return path;
    }

}
