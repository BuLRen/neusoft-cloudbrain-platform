package com.xikang.schedule;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
@MapperScan("com.xikang.schedule.mapper")
@EnableFeignClients
public class ScheduleApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(ScheduleApplication.class, args);
    }

    /**
     * 主动加载 .env 到 System Properties，绕过 spring.config.import / bootstrap.yml 的不确定性。
     * 查找顺序：
     *   1. ./schedule-service/.env（jar 启动常见路径）
     *   2. ./../.env（模块根向上）
     *   3. ./../../.env（再向上）
     *   4. ./../../../.env
     *   5. 用户目录 D:/learn/大三下/Xikang Cloud Hospital/XIKANG/xikang-cloud-hospital/.env 兜底
     * 已存在的 system property 优先（不覆盖）。
     */
    private static void loadDotEnv() {
        File found = locateEnvFile();
        if (found == null) {
            System.out.println("[DotEnv] no .env found");
            return;
        }
        System.out.println("[DotEnv] loading " + found.getAbsolutePath());
        int loaded = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(found, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (value.isEmpty()) {
                    continue;
                }
                // 去掉可选的引号
                if ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                if (System.getProperty(key) == null) {
                    System.setProperty(key, value);
                    loaded++;
                }
            }
        } catch (Exception exception) {
            System.err.println("[DotEnv] failed: " + exception.getMessage());
            return;
        }
        System.out.println("[DotEnv] loaded " + loaded + " keys");
    }

    private static File locateEnvFile() {
        List<File> candidates = new ArrayList<>();
        candidates.add(new File(".env"));
        candidates.add(new File("../.env"));
        candidates.add(new File("../../.env"));
        candidates.add(new File("../../../.env"));
        candidates.add(new File("XIKANG/xikang-cloud-hospital/.env"));
        // 用户自定义兜底
        candidates.add(new File("D:/learn/大三下/Xikang Cloud Hospital/XIKANG/xikang-cloud-hospital/.env"));
        for (File candidate : candidates) {
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }
}
