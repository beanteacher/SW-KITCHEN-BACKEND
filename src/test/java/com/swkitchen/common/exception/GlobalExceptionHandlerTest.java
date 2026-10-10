package com.swkitchen.common.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
@Import(GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false) // 오류 형식만 본다. 인증은 AuthControllerTest
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("입력 검증 실패는 400 VALIDATION_ERROR 이고 errors 에 본문 경로가 담긴다")
    void validationError() throws Exception {
        mvc.perform(post("/test/valid").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"items\":[{\"name\":\"ok\"},{\"name\":\"\"}]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors.length()").value(2))
            .andExpect(jsonPath("$.errors[?(@.field == 'items[1].name')]").exists())
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("JSON 형식이 깨지면 400 VALIDATION_ERROR 이고 errors 는 없다")
    void malformedJson() throws Exception {
        mvc.perform(post("/test/valid").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("AppException 은 ErrorCode 의 상태·코드로 응답한다")
    void appException() throws Exception {
        mvc.perform(post("/test/app"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("예상 못 한 오류는 500 INTERNAL_ERROR 이고 내부 메시지를 응답에 넣지 않는다")
    void unexpected() throws Exception {
        mvc.perform(post("/test/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(not(containsString("secret-detail"))));
    }

    @Test
    @DisplayName("JSON 이 아닌 본문은 415 UNSUPPORTED_MEDIA_TYPE")
    void unsupportedMediaType() throws Exception {
        mvc.perform(post("/test/valid").contentType(MediaType.TEXT_PLAIN).content("x"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("권한 검사 실패는 500 이 아니라 403 FORBIDDEN")
    void accessDenied() throws Exception {
        mvc.perform(post("/test/denied"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    record Item(@NotBlank String name) {}

    record Body(@NotBlank String name, List<@Valid Item> items) {}

    @RestController
    static class TestController {

        @PostMapping("/test/valid")
        void valid(@Valid @RequestBody Body body) {
        }

        @PostMapping("/test/app")
        void app() {
            throw new AppException(ErrorCode.NOT_FOUND);
        }

        @PostMapping("/test/denied")
        void denied() {
            throw new AccessDeniedException("denied");
        }

        @PostMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("secret-detail");
        }
    }
}
