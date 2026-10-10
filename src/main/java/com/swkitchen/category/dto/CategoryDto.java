package com.swkitchen.category.dto;

import com.swkitchen.category.domain.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public class CategoryDto {

    // ── API 요청·응답 (Request / Response) ──

    // parentId 가 없으면 대분류
    public record CreateRequest(
            Long parentId,
            @NotBlank(message = "분류 이름을 입력해 주세요.") @Size(max = 20, message = "분류 이름은 20자 이하입니다.") String name,
            @NotNull(message = "약어를 입력해 주세요.") @Pattern(regexp = "^[A-Z]{2}$", message = "약어는 영문 대문자 2자입니다.") String abbr) {
    }

    // 보낸 값만 바꾼다 (null 이면 그대로). 부모는 바꾸지 않는다
    public record UpdateRequest(
            @Pattern(regexp = "(?s).*\\S.*", message = "분류 이름을 입력해 주세요.") @Size(max = 20, message = "분류 이름은 20자 이하입니다.") String name,
            @Pattern(regexp = "^[A-Z]{2}$", message = "약어는 영문 대문자 2자입니다.") String abbr) {
    }

    // categoryIds: 그 부모의 형제 분류 전체를 바꿀 순서대로
    public record ChangeOrderRequest(
            Long parentId,
            @NotEmpty(message = "분류 목록을 보내 주세요.") List<@NotNull(message = "분류 id 가 비었습니다.") Long> categoryIds) {
    }

    public record Response(Long id, Long parentId, String name, String abbr, int sortOrder) {

        public static Response from(Category category) {
            Long parentId = category.isTop() ? null : category.getParent().getId();
            return new Response(category.getId(), parentId, category.getName(), category.getAbbr(),
                category.getSortOrder());
        }
    }

    // 대분류만 children 을 가진다. 중분류는 빈 배열
    public record TreeResponse(Long id, String name, String abbr, int sortOrder, List<TreeResponse> children) {

        public static TreeResponse of(Category category, List<TreeResponse> children) {
            return new TreeResponse(category.getId(), category.getName(), category.getAbbr(), category.getSortOrder(),
                children);
        }
    }

    // productCount: 화면이 약어 잠금·삭제 막기를 미리 보여주는 데 쓴다. 대분류는 아래 중분류 합
    public record AdminTreeResponse(
            Long id,
            String name,
            String abbr,
            int sortOrder,
            long productCount,
            List<AdminTreeResponse> children) {

        public static AdminTreeResponse of(Category category, Map<Long, Long> productCounts,
                List<AdminTreeResponse> children) {
            long count = productCounts.getOrDefault(category.getId(), 0L)
                + children.stream().mapToLong(AdminTreeResponse::productCount).sum();
            return new AdminTreeResponse(category.getId(), category.getName(), category.getAbbr(),
                category.getSortOrder(), count, children);
        }
    }
}
