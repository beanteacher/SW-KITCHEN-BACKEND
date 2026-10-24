package com.swkitchen.common.config;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import java.util.regex.Pattern;
import org.hibernate.engine.jdbc.internal.FormatStyle;

/** 로컬 쿼리 로그. ? 를 값으로 채운 쿼리만 줄 맞춰 찍는다 (spy.properties 에 등록, 로컬만 켜짐) */
public class P6SpySqlFormat implements MessageFormattingStrategy {

    private static final Pattern DDL = Pattern.compile("^\\s*(create|alter|drop|comment)\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public String formatMessage(
            int connectionId,
            String now,
            long elapsed,
            String category,
            String prepared,
            String sql,
            String url) {
        if (sql == null || sql.isBlank()) {
            return "";
        }
        FormatStyle style = DDL.matcher(sql).find() ? FormatStyle.DDL : FormatStyle.BASIC;
        return style.getFormatter().format(sql);
    }
}
