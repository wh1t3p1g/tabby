package tabby.util;

import com.google.common.reflect.ClassPath;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collection;

/**
 * 来源gadgetinspector
 * 尝试用Reflections库 和 guava库的比较，结果guava获得的类比较多
 * 所以这里直接用gadget inspector的实现
 * @url https://github.com/JackOfMostTrades/gadgetinspector/blob/master/src/main/java/gadgetinspector/ClassResourceEnumerator.java
 * @author wh1t3P1g
 * @since 2020/10/22
 */
public class ClassResourceEnumerator {
    private final ClassLoader classLoader;

    public ClassResourceEnumerator(ClassLoader classLoader) throws IOException {
        this.classLoader = classLoader;
    }

    public Collection<String> getTargetClassLoaderClasses() throws IOException {
        Collection<String> result = new ArrayList<>();
        for (ClassPath.ClassInfo classInfo : ClassPath.from(classLoader).getAllClasses()) {
            if(classInfo.getName().startsWith("org.springframework") ||
                    classInfo.getName().contains("tabby")) // 规避非jdk
                continue;
            result.add(classInfo.getName());
        }
        return result;
    }

    public Collection<String> getAllClasses() throws IOException {
        Collection<String> result = new ArrayList<>(getRuntimeClasses());
        for (ClassPath.ClassInfo classInfo : ClassPath.from(classLoader).getAllClasses()) {
            result.add(classInfo.getName());
        }
        return result;
    }

    public Collection<String> getRuntimeClasses() throws IOException {
        // A hacky way to get the current JRE's rt.jar. Depending on the class loader, rt.jar may be in the
        // bootstrap classloader so all the JDK classes will be excluded from classpath scanning with this!
        // However, this only works up to Java 8, since after that Java uses some crazy module magic.
        URL stringClassUrl = Object.class.getResource("String.class");
        URLConnection connection = stringClassUrl.openConnection();
        Collection<String> result = new ArrayList<>();
        if (connection instanceof JarURLConnection) {
            URL runtimeUrl = ((JarURLConnection) connection).getJarFileURL();
            URLClassLoader classLoader = new URLClassLoader(new URL[]{runtimeUrl});

            for (ClassPath.ClassInfo classInfo : ClassPath.from(classLoader).getAllClasses()) {
                result.add(classInfo.getName());
            }
            return result;
        }

        // Try finding all the JDK classes using the Java9+ modules method:
        try {
            FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
            Files.walk(fs.getPath("/")).forEach(p -> {
                if (p.toString().toLowerCase().endsWith(".class")) {
                    result.add(new PathClassResource(p).toString());
                }
            });
        } catch (ProviderNotFoundException e) {
            // Do nothing; this is expected on versions below Java9
        }

        return result;
    }

    public static interface ClassResource {
        public InputStream getInputStream() throws IOException;
        public String getName();
    }

    private static class PathClassResource implements ClassResource {
        private final Path path;

        private PathClassResource(Path path) {
            this.path = path;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public String getName() {
            return path.toString();
        }
    }

    private static class ClassLoaderClassResource implements ClassResource {
        private final ClassLoader classLoader;
        private final String resourceName;

        private ClassLoaderClassResource(ClassLoader classLoader, String resourceName) {
            this.classLoader = classLoader;
            this.resourceName = resourceName;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return classLoader.getResourceAsStream(resourceName);
        }

        @Override
        public String getName() {
            return resourceName;
        }
    }
}
