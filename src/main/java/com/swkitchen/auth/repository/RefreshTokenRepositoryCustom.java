package com.swkitchen.auth.repository;

import java.time.LocalDateTime;

public interface RefreshTokenRepositoryCustom {

    long deleteUnusable(LocalDateTime now, LocalDateTime rotatedBefore);
}
