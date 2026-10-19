package com.swkitchen.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.swkitchen.TestcontainersConfig;
import com.swkitchen.auth.domain.Role;
import com.swkitchen.common.security.JwtProvider;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

// MockMvc 는 업로드 크기 한도를 검사하지 않아, 크기 초과만 실제 서버로 보낸다
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AdminImageControllerTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0};

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    S3Client s3Client;

    @LocalServerPort
    int port;

    @Test
    @DisplayName("올리면 201 과 키·주소를 주고, 저장소에 그대로 들어간다")
    void upload() throws Exception {
        String response = upload(new MockMultipartFile("file", "사진.png", "image/png", PNG), Role.STAFF)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.key").value(matchesPattern("products/[0-9a-f-]{36}\\.png")))
            .andReturn().getResponse().getContentAsString();

        String key = JsonPath.read(response, "$.data.key");
        assertThat(JsonPath.<String>read(response, "$.data.url")).isEqualTo("http://localhost:4566/sw-kitchen/" + key);
        ResponseBytes<GetObjectResponse> saved = s3Client.getObjectAsBytes(request -> request
            .bucket("sw-kitchen")
            .key(key));
        assertThat(saved.asByteArray()).isEqualTo(PNG);
    }

    @Test
    @DisplayName("이름만 이미지인 파일은 400, file 칸이 없으면 400 이다")
    void invalidFile() throws Exception {
        byte[] text = "이름만 사진".getBytes(StandardCharsets.UTF_8);

        upload(new MockMultipartFile("file", "사진.jpg", "image/jpeg", text), Role.STAFF)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("IMAGE_INVALID_TYPE"));
        upload(new MockMultipartFile("photo", "사진.png", "image/png", PNG), Role.STAFF)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("X-Requested-With 헤더가 없으면 로그인했어도 403 이다")
    void withoutAjaxHeader() throws Exception {
        mvc.perform(multipart("/api/v1/admin/image")
                .file(new MockMultipartFile("file", "사진.png", "image/png", PNG))
                .cookie(accessToken(Role.ADMIN)))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그인 안 하면 401, 고객은 403, 관리자는 201 이다")
    void permission() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "사진.png", "image/png", PNG);

        mvc.perform(multipart("/api/v1/admin/image").file(file).header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isUnauthorized());
        upload(file, Role.CUSTOMER)
            .andExpect(status().isForbidden());
        upload(file, Role.ADMIN)
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("10MB 를 넘으면 400 이다")
    void tooLarge() throws Exception {
        byte[] content = Arrays.copyOf(PNG, 10 * 1024 * 1024 + 1);
        String boundary = "boundary";
        byte[] head = ("--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"big.png\"\r\n"
            + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/admin/image"))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Cookie", "access_token=" + jwtProvider.createAccessToken(1L, Role.STAFF))
            .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(head, content, tail)))
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("IMAGE_TOO_LARGE");
    }

    private ResultActions upload(MockMultipartFile file, Role role) throws Exception {
        return mvc.perform(multipart("/api/v1/admin/image")
            .file(file)
            .header("X-Requested-With", "XMLHttpRequest")
            .cookie(accessToken(role)));
    }

    private Cookie accessToken(Role role) {
        return new Cookie("access_token", jwtProvider.createAccessToken(1L, role));
    }
}
