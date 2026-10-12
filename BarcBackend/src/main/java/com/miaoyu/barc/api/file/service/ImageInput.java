package com.miaoyu.barc.api.file.service;

import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.response.ErrorR;
import java.io.IOException;
import java.util.Map;
import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;

/** 按文件头识别位图格式，拒绝 SVG/HTML 与伪造的 Content-Type；大小在读取前检查。 */
public final class ImageInput {
    private static final Map<String, String> TYPES = Map.of("image/png", "png", "image/jpeg", "jpg", "image/gif", "gif", "image/webp", "webp");
    private ImageInput() { }
    public static String extension(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty() || file.getSize() > maxBytes) throw invalid("图片为空或超过大小限制");
        try (var input = file.getInputStream()) {
            String mime = new Tika().detect(input);
            String extension = TYPES.get(mime);
            if (extension == null || (file.getContentType() != null && !file.getContentType().equalsIgnoreCase(mime))) {
                throw invalid("图片格式与实际内容不符，仅支持 PNG/JPEG/GIF/WebP");
            }
            return extension;
        } catch (IOException failure) { throw invalid("无法读取上传图片"); }
    }
    private static ApiException invalid(String text) { return new ApiException(new ErrorR().normal(text)); }
}
