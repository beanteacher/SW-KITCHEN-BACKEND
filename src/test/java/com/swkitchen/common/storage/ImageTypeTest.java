package com.swkitchen.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swkitchen.common.exception.AppException;
import com.swkitchen.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImageTypeTest {

    @Test
    @DisplayName("파일 앞부분으로 JPG·PNG·WEBP 를 알아보고 저장할 확장자·형식을 준다")
    void detect() {
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0};
        byte[] webp = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

        assertThat(ImageType.detect(jpg)).isEqualTo(ImageType.JPG);
        assertThat(ImageType.detect(png)).isEqualTo(ImageType.PNG);
        assertThat(ImageType.detect(webp)).isEqualTo(ImageType.WEBP);
        assertThat(ImageType.WEBP.getExtension()).isEqualTo("webp");
        assertThat(ImageType.WEBP.getContentType()).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("이미지가 아닌 파일, RIFF 로 시작하는 다른 파일, 빈 파일·짧은 파일은 거부한다")
    void invalid() {
        byte[] text = "이름만 사진.jpg".getBytes(StandardCharsets.UTF_8);
        byte[] wav = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'A', 'V', 'E'};
        byte[] gif = "GIF89a".getBytes(StandardCharsets.US_ASCII);
        byte[] cut = {(byte) 0xFF, (byte) 0xD8};

        for (byte[] content : new byte[][] {text, wav, gif, cut, new byte[0]}) {
            assertThatThrownBy(() -> ImageType.detect(content))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode())
                    .isEqualTo(ErrorCode.IMAGE_INVALID_TYPE));
        }
    }
}
