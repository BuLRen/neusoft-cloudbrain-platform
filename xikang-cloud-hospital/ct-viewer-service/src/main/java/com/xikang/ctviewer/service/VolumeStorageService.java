package com.xikang.ctviewer.service;

import com.xikang.common.exception.BusinessException;
import com.xikang.ctviewer.config.CtViewerProperties;
import com.xikang.ctviewer.dto.VolumeMetaDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class VolumeStorageService {

    private final CtViewerProperties properties;
    private Path workRoot;

    @PostConstruct
    void init() throws IOException {
        workRoot = Path.of(properties.getWorkDir()).toAbsolutePath().normalize();
        Files.createDirectories(workRoot);
        Files.createDirectories(workRoot.resolve("volumes"));
    }

    public String newVolumeId() {
        return UUID.randomUUID().toString();
    }

    public Path volumeRoot(String volumeId) {
        return workRoot.resolve("volumes").resolve(volumeId);
    }

    public Path nrrdPath(String volumeId) {
        return volumeRoot(volumeId).resolve("data.nrrd");
    }

    public Path srcDir(String volumeId) {
        return volumeRoot(volumeId).resolve("src");
    }

    public Path exportPath(String volumeId, String suffix) {
        return volumeRoot(volumeId).resolve("export" + suffix);
    }

    public void ensureVolumeDir(String volumeId) throws IOException {
        Files.createDirectories(volumeRoot(volumeId));
    }

    public void saveUploadedFile(MultipartFile file, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void saveDicomFiles(MultipartFile[] files, Path dicomDir) throws IOException {
        Files.createDirectories(dicomDir);
        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            if (file == null || file.isEmpty()) {
                continue;
            }
            String original = file.getOriginalFilename();
            String safeName = StringUtils.hasText(original) ? Path.of(original).getFileName().toString() : ("dicom_" + i + ".dcm");
            Path out = dicomDir.resolve(String.format("%04d_%s", i, safeName));
            saveUploadedFile(file, out);
        }
    }

    public byte[] readNrrdBytes(VolumeMetaDto meta) {
        try {
            Path path = Path.of(meta.getNrrdPath());
            if (!Files.isRegularFile(path)) {
                throw new BusinessException(404, "体数据文件不存在");
            }
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new BusinessException(500, "读取体数据失败", ex);
        }
    }

    public void deleteVolumeDirectory(String volumeId) {
        Path root = volumeRoot(volumeId);
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            });
        } catch (IOException ex) {
            // best effort cleanup
        }
    }

    public String absolutePath(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }
}
