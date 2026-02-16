package fun.sakuraspark.sakuraupdater;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class SakuraUpdaterBootstrap {
    private static final String LOG_FILE_PREFIX = "logs/sakuraupdater-";
    private static final String LOG_FILE_SUFFIX = ".log";
    private static final DateTimeFormatter LOG_FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    
    private static PromptManager promptManager;

    private static final String EMBEDDED_JAR_PREFIX = "META-INF/jarjar/";
    private static final String MAIN_CLASS = "fun.sakuraspark.sakuraupdater.SakuraUpdaterServerOnly";

    static {
        // 使用 slf4j-simple 输出控制台日志，尽量接近原版 Minecraft 格式
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "[HH:mm:ss]");
        System.setProperty("org.slf4j.simpleLogger.showThreadName", "true");
        System.setProperty("org.slf4j.simpleLogger.showLogName", "true");
        System.setProperty("org.slf4j.simpleLogger.showShortLogName", "true");
        System.setProperty("org.slf4j.simpleLogger.levelInBrackets", "true");
        System.setProperty("org.slf4j.simpleLogger.logFile", "System.out");
        
        // 完全禁用 Log4j2，防止加载 Forge 的日志配置
        System.setProperty("log4j2.disable.jmx", "true");
        System.setProperty("log4j.skipJansi", "true");
        System.setProperty("log4j2.formatMsgNoLookups", "true");
        System.setProperty("log4j.configurationFile", "");
        System.setProperty("log4j2.configurationFile", "");
        System.setProperty("log4j.configuration", "");
    }

    private SakuraUpdaterBootstrap() {
    }

    private static final class PromptManager {
        public final AtomicBoolean awaitingInput = new AtomicBoolean(false);
        public final String promptText;

        private PromptManager(String promptText) {
            this.promptText = promptText;
        }
    }

    private static String buildLogFileName() {
        String timestamp = LocalDateTime.now().format(LOG_FILE_TIME_FORMAT);
        return LOG_FILE_PREFIX + timestamp + LOG_FILE_SUFFIX;
    }

    private static void setupTeeOutput(String logPath) throws IOException {
        Path path = Path.of(logPath);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        PrintStream consoleOut = System.out;
        PrintStream consoleErr = System.err;
        PrintStream fileOut = new PrintStream(Files.newOutputStream(path), true, "UTF-8");

        System.setOut(new PrintStream(new PromptingTeeOutputStream(consoleOut, fileOut, promptManager), true, "UTF-8"));
        System.setErr(new PrintStream(new PromptingTeeOutputStream(consoleErr, fileOut, promptManager), true, "UTF-8"));
    }

    private static final class PromptingTeeOutputStream extends OutputStream {
        private final PrintStream console;
        private final PrintStream file;
        private final PromptManager promptManager;

        private PromptingTeeOutputStream(PrintStream console, PrintStream file, PromptManager promptManager) {
            this.console = console;
            this.file = file;
            this.promptManager = promptManager;
        }

        @Override
        public void write(int b) throws IOException {
            console.write(b);
            file.write(b);
            if (b == '\n') {
                // 只在控制台重刷提示符
                if (promptManager.awaitingInput.get()) {
                    console.print(promptManager.promptText);
                    console.flush();
                }
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            console.write(b, off, len);
            file.write(b, off, len);
            for (int i = off; i < off + len; i++) {
                if (b[i] == '\n') {
                    // 只在控制台重刷提示符
                    if (promptManager.awaitingInput.get()) {
                        console.print(promptManager.promptText);
                        console.flush();
                    }
                    break;
                }
            }
        }

        @Override
        public void flush() throws IOException {
            console.flush();
            file.flush();
        }
    }

    public static void main(String[] args) throws Exception {
        // 设置主线程名称
        Thread.currentThread().setName("Server thread");
        
        // 初始化提示符管理器
        promptManager = new PromptManager("> ");
        
        // 同时输出到文件（stdout + file）
        try {
            setupTeeOutput(buildLogFileName());
        } catch (IOException ignored) {
            // Fallback to stdout only if file setup fails.
        }
        
        URL selfJar = getSelfJarUrl();
        URL[] embeddedJars = extractEmbeddedJars();
        if (selfJar == null || embeddedJars.length == 0) {
            invokeMain(MAIN_CLASS, SakuraUpdaterBootstrap.class.getClassLoader(), args);
            return;
        }

        URL[] urls = new URL[embeddedJars.length + 1];
        urls[0] = selfJar;
        System.arraycopy(embeddedJars, 0, urls, 1, embeddedJars.length);

        try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
            Thread.currentThread().setContextClassLoader(loader);
            invokeMain(MAIN_CLASS, loader, args);
        }
    }

    private static void invokeMain(String mainClass, ClassLoader loader, String[] args) throws Exception {
        Class<?> target = Class.forName(mainClass, true, loader);
        Method main = target.getMethod("main", String[].class);
        while (main.invoke(null, (Object) args) instanceof Integer result && result == 1); // 雷霆写法
        
    }

    private static URL getSelfJarUrl() {
        CodeSource source = SakuraUpdaterBootstrap.class.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            return null;
        }

        URL location = source.getLocation();
        if (!location.getPath().endsWith(".jar")) {
            return null;
        }

        return location;
    }

    private static URL[] extractEmbeddedJars() throws IOException {
        CodeSource source = SakuraUpdaterBootstrap.class.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            return new URL[0];
        }

        URL location = source.getLocation();
        if (!location.getPath().endsWith(".jar")) {
            return new URL[0];
        }

        Path jarPath;
        try {
            jarPath = Path.of(location.toURI());
        } catch (Exception e) {
            return new URL[0];
        }
        if (!Files.isRegularFile(jarPath)) {
            return new URL[0];
        }

        List<URL> urls = new ArrayList<>();
        Path tempDir = Files.createTempDirectory("sakuraupdater-jarjar-");
        tempDir.toFile().deleteOnExit();

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().startsWith(EMBEDDED_JAR_PREFIX)
                        && entry.getName().endsWith(".jar")) {
                    Path out = tempDir.resolve(Path.of(entry.getName()).getFileName().toString());
                    out.toFile().deleteOnExit();
                    try (InputStream in = jarFile.getInputStream(entry)) {
                        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                    urls.add(out.toUri().toURL());
                }
            }
        }

        return urls.toArray(new URL[0]);
    }
}
