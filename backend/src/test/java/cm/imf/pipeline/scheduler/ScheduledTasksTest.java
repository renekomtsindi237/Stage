package cm.imf.pipeline.scheduler;

import cm.imf.pipeline.repository.RefreshTokenRepository;
import cm.imf.pipeline.service.INotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduledTasks — tests unitaires")
class ScheduledTasksTest {

    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock CacheManager cacheManager;
    @Mock JdbcTemplate jdbc;
    @Mock INotificationService notificationService;
    @InjectMocks ScheduledTasks scheduledTasks;

    @Test
    @DisplayName("cleanupExpiredTokens — appelle deleteByExpiresAtBefore avec une date passée")
    void cleanupExpiredTokens_appelle_repository() {
        when(refreshTokenRepository.deleteByExpiresAtBefore(any(OffsetDateTime.class))).thenReturn(3);

        scheduledTasks.cleanupExpiredTokens();

        verify(refreshTokenRepository).deleteByExpiresAtBefore(any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("cleanupExpiredTokens — aucun token expiré → pas de log warn")
    void cleanupExpiredTokens_aucun_token() {
        when(refreshTokenRepository.deleteByExpiresAtBefore(any())).thenReturn(0);

        scheduledTasks.cleanupExpiredTokens();

        verify(refreshTokenRepository).deleteByExpiresAtBefore(any());
    }

    @Test
    @DisplayName("evictKpiCaches — invalide tous les caches nommés")
    void evictKpiCaches_invalide_tous_les_caches() {
        Cache mockCache = mock(Cache.class);
        when(cacheManager.getCache(anyString())).thenReturn(mockCache);

        scheduledTasks.evictKpiCaches();

        // 9 caches : kpi-par, kpi-collectes, kpi-dashboard, prets-list, prets-agent,
        //            clients-search, agents-agence, agents-list, agents-search
        verify(cacheManager, times(9)).getCache(anyString());
        verify(mockCache, times(9)).clear();
    }
}
