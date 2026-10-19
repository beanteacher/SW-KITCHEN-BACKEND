package com.swkitchen.product.service;

import com.swkitchen.common.config.ImageProperties;
import com.swkitchen.common.storage.ImageStorage;
import com.swkitchen.common.storage.ImageType;
import com.swkitchen.product.dto.ProductDto;
import java.io.IOException;
import java.io.UncheckedIOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 제품 이미지를 먼저 올려 키를 받아 두고, 제품을 저장할 때 그 키를 보낸다 */
@Service
@RequiredArgsConstructor
public class ImageService {

    private final ImageStorage imageStorage;
    private final ImageProperties imageProperties;

    public ProductDto.ImageResponse upload(MultipartFile file) {
        byte[] content = read(file);
        ImageType type = ImageType.detect(content);
        String key = imageStorage.upload(content, type);

        return ProductDto.ImageResponse.of(key, imageProperties::urlOf);
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
