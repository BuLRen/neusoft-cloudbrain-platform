package com.xikang.common.env;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 加载项目根目录下的 .env 文件到系统属性。
 *
 * <p>Spring 的 @Value 占位符（如 ${DEEPSEEK_API_KEY}）默认从 Environment 解析，
 * 而 Environment 的查找顺序是：JVM 系统属性 > 环境变量 > application.yml。
 * 把 .env 里的 KEY=VALUE 写入 System.setProperty，
 * 即可在所有微服务统一生效，而无需把密钥写进 yml / 提交到 git。
 *
 * <p>使用方式（在每个服务的 Application.main 中调用一次）：
 * <pre>{@code
 * public static void main(String[] args) {
 *     EnvLoader.load();
 *     SpringApplication.run(AiConsultApplication.class, args);
 * }
 * }</pre>
 *
 * <p>查找顺序（命中即停）：
 * <ol>
 *   <li>user.dir/.env</li>
 *   <li>从 user.dir 向上逐层找 pom.xml，含 &lt;modules&gt; 的就是根 pom，
 *       根 pom 所在目录就是项目根，在项目根找 .env</li>
 * </ol>
 *
 * <p>不依赖项目根目录名（不写死 xikang-cloud-hospital），也不依赖 IDEA 的 working directory 设置。
 *
 * <p>已有的同名系统属性 / 环境变量不会被覆盖（保持部署环境的优先级）。
 */
public final class EnvLoader {

    private EnvLoader() {}

    /**
     * 加载 .env 到 System Properties。找不到文件静默返回，异常仅打印到 stderr。
     */
    public static void load() {
        // DEBUG: 排查 cwd
        System.out.println("[ENV] user.dir = " + System.getProperty("user.dir"));
        Path envPath = locateEnvFile();
        if (envPath == null || !Files.exists(envPath)) {
            System.out.println("[ENV] .env not found, skip loading");
            return;
        }

        try {
            Map<String, String> envVars = Files.lines(envPath)
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#") && line.contains("="))
                    .collect(Collectors.toMap(
                            line -> line.substring(0, line.indexOf('=')).trim(),
                            line -> unquote(line.substring(line.indexOf('=') + 1).trim()),
                            (a, b) -> b // 重复 key 取后者
                    ));

            int loaded = 0;
            for (Map.Entry<String, String> e : envVars.entrySet()) {
                String key = e.getKey();
                String value = e.getValue();
                // 已有同名的系统属性 / 环境变量就不覆盖（让部署环境优先）
                if (System.getProperty(key) == null && System.getenv(key) == null) {
                    System.setProperty(key, value);
                    System.out.println("[ENV] Loaded: " + key);
                    loaded++;
                }
            }
            System.out.println("[ENV] Loaded " + loaded + " entries from: " + envPath);
        } catch (IOException ex) {
            System.err.println("[ENV] Failed to load: " + envPath + ", reason: " + ex.getMessage());
        }
    }

    /**
     * 剥离 .env 字符串值的引号（支持 "value" 或 'value'）。
     */
    private static String unquote(String s) {
        if (s.length() >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }

    /**
     * 按优先级查找 .env 文件位置。
     *
     * <p>策略（命中即停）：
     * <ol>
     *   <li>user.dir/.env（JAR 部署目录）</li>
     *   <li>从 user.dir 向上找 Maven 根 pom（pom.xml 含 &lt;modules&gt;），在其目录找 .env</li>
     *   <li>从 user.dir 向下扫子目录找 Maven 根 pom，在其目录找 .env</li>
     * </ol>
     */
    private static Path locateEnvFile() {
        Path userDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();

        // 1) 运行目录下的 .env（宝塔 / 服务器 JAR 部署：JAR 与 .env 同目录即可）
        Path cwdEnv = userDir.resolve(".env");
        if (Files.exists(cwdEnv)) {
            return cwdEnv;
        }

        // 1b) 单仓根目录启动（cwd=neusoft-cloudbrain-platform）时 .env 在 xikang-cloud-hospital 子目录
        Path nestedHospitalEnv = userDir.resolve("xikang-cloud-hospital").resolve(".env");
        if (Files.exists(nestedHospitalEnv)) {
            return nestedHospitalEnv;
        }

        // 2) 从 user.dir 向上找 Maven 根 pom
        Path rootDir = findProjectRootUpward(userDir);
        if (rootDir != null) {
            Path envPath = rootDir.resolve(".env");
            if (Files.exists(envPath)) {
                return envPath;
            }
        }

        // 3) 从 user.dir 向下扫子目录（兼容 cwd 在项目外层的情况）
        Path rootDirDown = findProjectRootDownward(userDir, 3);
        if (rootDirDown != null) {
            Path envPath = rootDirDown.resolve(".env");
            if (Files.exists(envPath)) {
                return envPath;
            }
        }

        return null;
    }

    /**
     * 从 startDir 向下递归扫描（最多 depth 层），找第一个含 &lt;modules&gt; 的 pom.xml。
     * 用于 cwd 在项目外层目录（如 XIKANG/）的情况。
     */
    private static Path findProjectRootDownward(Path startDir, int maxDepth) {
        if (maxDepth < 0 || startDir == null || !Files.isDirectory(startDir)) {
            return null;
        }
        Path pom = startDir.resolve("pom.xml");
        if (Files.isRegularFile(pom) && isRootPom(pom)) {
            return startDir;
        }
        // 限深递归子目录
        try (var stream = Files.list(startDir)) {
            for (Path child : (Iterable<Path>) stream::iterator) {
                if (Files.isDirectory(child)) {
                    Path found = findProjectRootDownward(child, maxDepth - 1);
                    if (found != null) return found;
                }
            }
        } catch (IOException ignored) {}
        return null;
    }

    /**
     * 从 startDir 出发，向上逐层找 Maven 根 pom（含 &lt;modules&gt; 的 pom.xml），
     * 找到返回根 pom 所在目录；走到文件系统根都没找到返回 null。
     *
     * <p>判断标准：pom.xml 文件内容里包含 "&lt;modules&gt;" 节点（最严格且不依赖 pom 命名约定）。
     */
    private static Path findProjectRootUpward(Path startDir) {
        Path current = startDir;
        while (current != null) {
            Path pom = current.resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                if (isRootPom(pom)) {
                    return current;
                }
                // 是子 pom，继续向上找（很可能父目录就是根）
                current = current.getParent();
            } else {
                // 当前目录没 pom.xml，向上找
                current = current.getParent();
            }
        }
        return null;
    }

    /**
     * 判断一个 pom.xml 是否为根 pom（含 &lt;modules&gt; 节点）。
     * 用一个轻量的字符串扫描，避免引入 XML 解析依赖。
     */
    private static boolean isRootPom(Path pomFile) {
        try {
            String content = Files.readString(pomFile);
            // 多行 / 单行 / 带属性都容忍
            return content.matches("(?s).*<modules\\b[^>]*>.*")
                || content.matches("(?s).*<modules\\s*/>.*");
        } catch (IOException e) {
            return false;
        }
    }
}
