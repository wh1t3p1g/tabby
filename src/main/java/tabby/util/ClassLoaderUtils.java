package tabby.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * @author wh1t3P1g
 * @since 2020/10/22
 * 处理目标jar的classloader，参考gadget inspector
 */
@Slf4j
public class ClassLoaderUtils {

    /**
     * 获取目录下的所有jar的classloader
     * @param jarPaths
     * @return
     * @throws IOException
     */
    public static ClassLoader getClassLoader(Path ... jarPaths) throws IOException {
        final List<URL> classPathUrls = new ArrayList<>(jarPaths.length);

        for (Path jarPath : jarPaths) {
            if (!Files.exists(jarPath) || Files.isDirectory(jarPath)) {
                throw new IllegalArgumentException("Path \"" + jarPath + "\" is not a path to a file.");
            }
            classPathUrls.add(jarPath.toUri().toURL());
        }

        return new URLClassLoader(classPathUrls.toArray(new URL[0]));
    }

    public static ClassLoader getClassLoader(List<String> jarPaths) throws MalformedURLException {
        final List<URL> classPathUrls = new ArrayList<>(jarPaths.size());
        for (String jarPath : jarPaths) {
            Path jarRealPath = Paths.get(jarPath).toAbsolutePath();
            if (!Files.exists(jarRealPath)) {
                throw new IllegalArgumentException("Path \"" + jarPath + "\" is not a path to a file.");
            }
            classPathUrls.add(jarRealPath.toUri().toURL());
        }

        return new URLClassLoader(classPathUrls.toArray(new URL[0]));
    }

    /**
     * 从目标jar或者war中提取出libs的classloader
     * 针对单jar应用 或者 单war应用 （第三方单一jar不用这种方式来获取）
     * 一般都是war包来分析
     * @param jarPath 目标路径
     * @param type 类型，jar or war
     * @return 返回提取后的classloader
     * @throws IOException
     */
    public static ClassLoader getClassLoader(Path jarPath, String type) throws IOException {
        // TODO 当前版本没有考虑jar文件内可能存在的lib目录里的jar文件
        String tempDirectory = "jar".equals(type)? "exploded-jar" : "exploded-war";
//        String classesDirectory = "jar".equals(type) ? "BOOT-INF/classes" : "WEB-INF/classes";
        String libDirectory = "jar".equals(type) ? "BOOT-INF/lib" : "WEB-INF/lib";

        Path tmpDir = registerTempDirectory(tempDirectory);

        extractFromJarPath(jarPath, tmpDir);

        final List<URL> classPathUrls = new ArrayList<>();
//        classPathUrls.add(tmpDir.resolve(classesDirectory).toUri().toURL());
        Files.list(tmpDir.resolve(libDirectory)).forEach(p -> {
            try {
                classPathUrls.add(p.toUri().toURL());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        });

        return new URLClassLoader(classPathUrls.toArray(new URL[0]));
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

    /**
     * 将目标解压到临时路径上，主要针对war类型
     * @param jarPath 待分析的jar路径
     * @param tmpDir 提取后的临时路径
     * @throws IOException
     */
    public static void extractFromJarPath(Path jarPath, Path tmpDir) throws IOException {

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


    public static List<String> getAllClasses(List<String> targets){
        List<String> runtimeClasses = null;
        try {
            ClassResourceEnumerator classResourceEnumerator =
                    new ClassResourceEnumerator(ClassLoaderUtils.getClassLoader(targets));
            runtimeClasses = (List<String>) classResourceEnumerator.getTargetClassLoaderClasses();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return runtimeClasses;
    }

}
