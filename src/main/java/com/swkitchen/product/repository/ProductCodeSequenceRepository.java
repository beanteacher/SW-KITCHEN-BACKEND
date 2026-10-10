package com.swkitchen.product.repository;

import com.swkitchen.product.domain.ProductCodeSequence;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ProductCodeSequenceRepository extends JpaRepository<ProductCodeSequence, String> {

    // 같은 접두어로 동시에 등록해도 번호가 겹치지 않게 행을 잠근다 (SELECT ... FOR UPDATE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProductCodeSequence> findByPrefix(String prefix);
}
