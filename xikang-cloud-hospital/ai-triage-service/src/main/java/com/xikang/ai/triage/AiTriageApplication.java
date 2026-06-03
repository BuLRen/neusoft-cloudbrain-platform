package com.xikang.ai.triage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI Triage Service Application
 */
@SpringBootApplication
public class AiTriageApplication {

    public static void main(String[] args) {
        // 加载 .env 文件到系统属性（让 Spring @Value 能读取）
        loadEnvFile();
        SpringApplication.run(AiTriageApplication.class, args);
    }

    /**
     * 从项目根目录加载 .env 文件
     * .env 文件位置: xikang-cloud-hospital/.env
     */
    private static void loadEnvFile() {
        // 方式1: 尝试从 user.dir (Maven 运行目录)
        Path candidate = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path envPath = candidate.resolve(".env");

        // 方式2: 如果 user.dir 下没有，往上找 xikang-cloud-hospital
        if (!Files.exists(envPath)) {
            Path current = candidate;
            while (current != null && current.getParent() != null) {
                Path parent = current.getParent();
                Path potential = parent.resolve("xikang-cloud-hospital/.env");
                if (Files.exists(potential)) {
                    envPath = potential;
                    break;
                }
                if (current.getFileName().toString().equals("xikang-cloud-hospital")) {
                    envPath = current.resolve(".env");
                    break;
                }
                current = parent;
            }
        }

        // 方式3: 硬编码路径
        if (!Files.exists(envPath)) {
            Path parentOfXikang = Paths.get("D:/learn/大三下/Xikang Cloud Hospital/XIKANG");
            Path projectRoot = parentOfXikang.resolve("xikang-cloud-hospital");
            envPath = projectRoot.resolve(".env");
        }

        if (Files.exists(envPath)) {
            try {
                Map<String, String> envVars = Files.lines(envPath)
                        .filter(line -> line.contains("=") && !line.trim().startsWith("#"))
                        .collect(Collectors.toMap(
                                line -> line.substring(0, line.indexOf("=")).trim(),
                                line -> line.substring(line.indexOf("=") + 1).trim()
                        ));

                envVars.forEach((key, value) -> {
                    // 设置系统属性，让 Spring @Value 能读取到
                    if (System.getProperty(key) == null) {
                        System.setProperty(key, value);
                        System.out.println("[ENV] Loaded: " + key);
                    }
                });
                System.out.println("[ENV] Loaded from: " + envPath);
            } catch (IOException e) {
                System.err.println("[ENV] Failed to load: " + envPath);
            }
        } else {
            System.out.println("[ENV] .env not found at: " + envPath);
        }
    }
}