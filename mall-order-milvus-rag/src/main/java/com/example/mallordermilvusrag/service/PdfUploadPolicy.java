package com.example.mallordermilvusrag.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Objects;

@Component
public class PdfUploadPolicy {

    private static final long MAX_PDF_BYTES = 20L * 1024 * 1024;

    /**
     * 校验上传文件非空、大小不超过 20 MB 且扩展名为 PDF，并清理路径信息。
     *
     * @param file 上传文件
     * @return 仅包含文件名的安全名称
     * @throws IllegalArgumentException 文件不符合上传策略时抛出
     */
    public String validateAndResolveFilename(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("PDF file must not be empty");
        }
        if (file.getSize() > MAX_PDF_BYTES) {
            throw new IllegalArgumentException("PDF file must not exceed 20 MB");
        }
        String original = StringUtils.cleanPath(Objects.requireNonNullElse(file.getOriginalFilename(), ""));
        String filename = StringUtils.getFilename(original);
        if (filename == null || filename.isBlank()
                || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are supported");
        }
        return filename;
    }
}
