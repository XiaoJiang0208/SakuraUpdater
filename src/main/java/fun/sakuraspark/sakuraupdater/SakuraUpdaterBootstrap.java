package fun.sakuraspark.sakuraupdater;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class SakuraUpdaterBootstrap {

    // 需要解压的依赖列表（和你 build.gradle 中添加的对应）
    private static final String[] LIBS = {
        "sqlite-jdbc-3.46.0.0.jar",
        "gson-2.10.jar",
        "toml-3.8.1.jar",
        "core-3.8.1.jar",
        "slf4j-api-2.0.9.jar",
        "slf4j-simple-2.0.9.jar"
    };

    public static void main(String[] args) {
        try {
            // 设定外部解压目录，可以放在当前运行目录的 lib 下
            File libDir = new File("lib");
            if (!libDir.exists()) {
                libDir.mkdirs();
            }

            List<URL> urls = new ArrayList<>();

            // 将自己的 jar 本身也放入 ClassLoader 的路径中
            urls.add(SakuraUpdaterBootstrap.class.getProtectionDomain().getCodeSource().getLocation());

            // 解压依赖并收集 URL
            for (String lib : LIBS) {
                File targetFile = new File(libDir, lib);
                // 只有当文件不存在时才释放，加快后续启动速度
                if (!targetFile.exists()) {
                    try (InputStream is = SakuraUpdaterBootstrap.class.getResourceAsStream("/standaloneLibs/" + lib)) {
                        if (is == null) {
                            System.err.println("警告: 无法在 Jar 内找到依赖 " + lib);
                            continue;
                        }
                        Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                urls.add(targetFile.toURI().toURL());
            }

            // 构建新的 ClassLoader
            // 使用 PlatformClassLoader 作为 parent，强迫我们的 customLoader 接管此 jar 中的所有业务类
            URLClassLoader customLoader = new URLClassLoader(
                urls.toArray(new URL[0]),
                ClassLoader.getPlatformClassLoader()
            );

            // 将当前线程上下文的 ClassLoader 替换为我们的 CustomLoader（对 SLF4J 这种依靠上下文寻找实现的库极为重要）
            Thread.currentThread().setContextClassLoader(customLoader);

            // 反射加载并执行 真正的 Main 逻辑类
            // 注意：此时你的真实 Main 类在加载时，就能完全看到 lib 目录下的所有依赖了！
            Class<?> realMainClass = customLoader.loadClass("fun.sakuraspark.sakuraupdater.SakuraUpdaterServerStandalone");
            Method mainMethod = realMainClass.getDeclaredMethod("main", String[].class);
            mainMethod.invoke(null, (Object) args);

        } catch (Exception e) {
            System.err.println("通过 Bootstrap 启动失败!");
            e.printStackTrace();
            System.exit(1);
        }
    }
}