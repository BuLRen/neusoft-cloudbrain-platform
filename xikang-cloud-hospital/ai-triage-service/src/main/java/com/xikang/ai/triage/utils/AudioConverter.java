package com.xikang.ai.triage.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.*;

/**
 * 音频格式转换工具
 * 使用系统 FFmpeg 将 WebM/Opus 转换为 WAV (16000Hz, 16bit, mono)
 * FFmpeg 必须在 PATH 中或可通过绝对路径访问
 */
@Slf4j
public class AudioConverter {

    /** 查找 FFmpeg 可执行文件路径 */
    private static String ffmpegPath = null;

    private static String findFfmpeg() {
        if (ffmpegPath != null) return ffmpegPath;

        // 常见路径
        String[] candidates = {
            "ffmpeg",
            "D:/ffmpeg/bin/ffmpeg.exe",
            "C:/ffmpeg/bin/ffmpeg.exe",
            "/usr/bin/ffmpeg",
            "/usr/local/bin/ffmpeg"
        };

        for (String path : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(path, "-version");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String output = new String(p.getInputStream().readAllBytes());
                if (p.waitFor() == 0 && output.contains("ffmpeg")) {
                    ffmpegPath = path;
                    log.info("[AudioConverter] 找到 FFmpeg: {}", path);
                    return path;
                }
            } catch (Exception e) {
                // 继续尝试下一个
            }
        }

        log.warn("[AudioConverter] 未找到 FFmpeg，将尝试使用 'ffmpeg'");
        ffmpegPath = "ffmpeg";
        return ffmpegPath;
    }

    /**
     * 将 WebM/Opus 音频数据转换为 WAV (16000Hz, 16bit PCM, mono)
     *
     * @param inputAudio 输入音频数据（WebM/Opus格式）
     * @return WAV 格式音频数据，失败返回 null
     */
    public static byte[] convertToWav(byte[] inputAudio) {
        if (inputAudio == null || inputAudio.length < 1000) {
            log.warn("[AudioConverter] 输入音频太短: {} bytes", inputAudio == null ? 0 : inputAudio.length);
            return null;
        }

        String ffmpeg = findFfmpeg();
        File inputFile = null;
        File outputFile = null;

        try {
            // 创建临时文件
            inputFile = File.createTempFile("audio_input_", ".webm");
            outputFile = File.createTempFile("audio_output_", ".wav");

            // 写入输入音频
            try (FileOutputStream fos = new FileOutputStream(inputFile)) {
                fos.write(inputAudio);
            }

            // 执行 FFmpeg 转换
            // -ar 16000: 采样率 16kHz
            // -ac 1: 单声道
            // -acodec pcm_s16le: 16bit PCM
            ProcessBuilder pb = new ProcessBuilder(
                    ffmpeg,
                    "-y",                    // 覆盖输出文件
                    "-i", inputFile.getAbsolutePath(),   // 输入文件
                    "-ar", "16000",          // 采样率
                    "-ac", "1",               // 单声道
                    "-acodec", "pcm_s16le",   // 16bit PCM
                    outputFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String stderr = new String(process.getInputStream().readAllBytes());

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("[AudioConverter] FFmpeg 转换失败: exitCode={}, stderr={}", exitCode, stderr);
                return null;
            }

            // 读取输出 WAV
            byte[] wavData;
            try (FileInputStream fis = new FileInputStream(outputFile)) {
                wavData = fis.readAllBytes();
            }

            log.info("[AudioConverter] 转换成功: {} bytes -> {} bytes (WAV)", inputAudio.length, wavData.length);
            return wavData;

        } catch (Exception e) {
            log.error("[AudioConverter] 转换异常", e);
            return null;
        } finally {
            // 清理临时文件
            if (inputFile != null) inputFile.delete();
            if (outputFile != null) outputFile.delete();
        }
    }
}