package com.swkitchen.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swkitchen.RepositoryTest;
import com.swkitchen.product.domain.ProductCodeSequence;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

@RepositoryTest
class ProductCodeSequenceRepositoryTest {

    @Autowired
    ProductCodeSequenceRepository sequenceRepository;

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("접두어마다 00001 부터 번호를 발급하고 마지막 번호를 저장한다")
    void issue() {
        sequenceRepository.saveAndFlush(ProductCodeSequence.create("RFUR"));
        sequenceRepository.saveAndFlush(ProductCodeSequence.create("WSWT"));

        ProductCodeSequence fridge = sequenceRepository.findByPrefix("RFUR").orElseThrow();
        assertThat(fridge.issue()).isEqualTo("RFUR-00001");
        assertThat(fridge.issue()).isEqualTo("RFUR-00002");
        em.flush();
        em.clear();

        assertThat(sequenceRepository.findByPrefix("RFUR").orElseThrow().issue()).isEqualTo("RFUR-00003");
        assertThat(sequenceRepository.findByPrefix("WSWT").orElseThrow().issue()).isEqualTo("WSWT-00001");
    }

    @Test
    @DisplayName("99999 번을 넘기면 DB 가 막는다")
    void maxNo() {
        ProductCodeSequence sequence = ProductCodeSequence.create("RFUR");
        ReflectionTestUtils.setField(sequence, "lastNo", 99_999);
        // 접두어를 직접 정하는 엔티티라 save 는 넘긴 객체가 아니라 저장된 복사본을 돌려준다
        ProductCodeSequence saved = sequenceRepository.saveAndFlush(sequence);

        saved.issue();

        assertThatThrownBy(() -> sequenceRepository.flush())
            .hasMessageContaining("ck_product_code_sequence_no");
    }
}
