package com.swkitchen.category.dto;

import com.swkitchen.category.domain.Category;
import java.util.List;
import java.util.Map;

public class CategoryDto {

    // ── API 요청·응답 (Request / Response) ──

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
