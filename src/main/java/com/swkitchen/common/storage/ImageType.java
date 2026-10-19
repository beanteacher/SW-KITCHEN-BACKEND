package com.swkitchen.common.storage;

import com.swkitchen.common.exception.AppException;
import com.swkitchen.common.exception.ErrorCode;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 올릴 수 있는 이미지 형식. 확장자·브라우저가 보낸 형식은 바꿔 보낼 수 있어 믿지 않고 파일 앞부분으로 판별한다 */
@Getter
@RequiredArgsConstructor
public enum ImageType {

    JPG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp");

    private static final byte[] JPG_HEAD = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_HEAD = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    // WEBP 는 RIFF + 파일 길이 4바이트 + WEBP
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_MARK = {'W', 'E', 'B', 'P'};

    private final String extension;
    private final String contentType;

    public static ImageType detect(byte[] content) {
        if (startsWith(content, 0, JPG_HEAD)) {
            return JPG;
        }
        if (startsWith(content, 0, PNG_HEAD)) {
            return PNG;
        }
        if (startsWith(content, 0, RIFF) && startsWith(content, 8, WEBP_MARK)) {
            return WEBP;
        }
        throw new AppException(ErrorCode.IMAGE_INVALID_TYPE);
    }

    private static boolean startsWith(byte[] content, int offset, byte[] head) {
        return content.length >= offset + head.length
            && Arrays.equals(content, offset, offset + head.length, head, 0, head.length);
    }
}
