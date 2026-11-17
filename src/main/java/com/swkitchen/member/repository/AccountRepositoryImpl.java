package com.swkitchen.member.repository;

import static com.swkitchen.member.domain.QAccount.account;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.swkitchen.common.repository.LikePattern;
import com.swkitchen.member.domain.Account;
import com.swkitchen.member.domain.Role;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@RequiredArgsConstructor
public class AccountRepositoryImpl implements AccountRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final EntityManager em;

    // 읽고 더해서 쓰면 동시에 틀린 시도가 빠져서 DB 에서 바로 더한다. 읽어 둔 계정은 옛 값이 되므로 비운다
    @Override
    public void increaseLoginFailCount(Long accountId) {
        em.flush();
        queryFactory
            .update(account)
            .set(account.loginFailCount, account.loginFailCount.add(1))
            .where(account.id.eq(accountId))
            .execute();
        em.clear();
    }

    @Override
    public Page<Account> searchCustomers(String keyword, Pageable pageable) {
        BooleanExpression[] conditions = {account.role.eq(Role.CUSTOMER), userIdOrNameMatches(keyword)};

        List<Account> accounts = queryFactory
            .selectFrom(account)
            .where(conditions)
            .orderBy(account.id.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
        Long total = queryFactory
            .select(account.count())
            .from(account)
            .where(conditions)
            .fetchOne();

        return new PageImpl<>(accounts, pageable, total);
    }

    private static BooleanExpression userIdOrNameMatches(String keyword) {
        if (keyword == null) {
            return null;
        }
        String pattern = LikePattern.contains(keyword);
        return account.userId.like(pattern, LikePattern.ESCAPE).or(account.name.like(pattern, LikePattern.ESCAPE));
    }
}
