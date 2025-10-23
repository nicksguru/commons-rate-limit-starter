package guru.nicks.ratelimit.config;

import guru.nicks.ratelimit.impl.RateLimitServiceImpl;
import guru.nicks.ratelimit.service.RateLimitService;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper;
import io.github.bucket4j.distributed.proxy.ExpiredEntriesCleaner;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.postgresql.Bucket4jPostgreSQL;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.time.Duration;

/**
 * Configures Postgres-based Bucket4J storage and cron job for deleting expired buckets. See
 * <a href="https://bucket4j.com/8.13.1/toc.html#bucket4j-postgresql">Bucket4J manual</a> for details.
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class RateLimitAutoConfiguration {

    @ConditionalOnMissingBean(RateLimitService.class)
    @Bean
    public RateLimitService rateLimitService(ProxyManager<String> proxyManager) {
        return new RateLimitServiceImpl(proxyManager);
    }

    @ConditionalOnMissingBean(ProxyManager.class)
    @Bean
    public ProxyManager<String> bucket4jProxyManager(DataSource dataSource) {
        return Bucket4jPostgreSQL.selectForUpdateBasedBuilder(dataSource)
                .table("bucket4j")
                // primary keys are strings, such as recipient phone numbers
                .primaryKeyMapper(PrimaryKeyMapper.STRING)
                // thus buckets are not persisted in storage longer than time required to refill all consumed tokens
                // (provided that there's a cron job that deletes them, see @Scheduled)
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                        Duration.ofSeconds(60)))
                .build();
    }

    /**
     * Removes expired buckets from DB periodically according to {@value #CRON_EXPRESSION}.
     */
    @Configuration(proxyBeanMethods = false)
    @RequiredArgsConstructor
    @Slf4j
    public static class ExpiresBucketCleanerCronJob {

        /**
         * At x:30 of every hour 'x': 00:00, 00:30, 01:00, 01:30, etc.
         */
        public static final String CRON_EXPRESSION = "0 */30 * * * *";

        /**
         * How many buckets to remove at once (i.e. with one SQL statement).
         */
        private static final int BATCH_SIZE = 200;

        /**
         * During batch removal, some other buckets may expire. If this threshold is low, it's possible, under a very
         * high load on the rate limiting facility, that all buckets will never be removed due to eternal loop.
         */
        private static final int MIN_ACTUALLY_REMOVED_TO_PROCESS_NEW_BATCH = 50;

        /**
         * DB-powered {@link ProxyManager} created in outer class. Redis-powered one doesn't implement this interface
         * (moreover, in Redis, bucket TTLs are set to -1 because the default CacheManager is used, which means no
         * bucket expiration at all).
         */
        private final ExpiredEntriesCleaner proxyManager;

        @Scheduled(cron = CRON_EXPRESSION)
        public void scheduleFixedDelayTask() {
            int removedKeysCount;

            do {
                removedKeysCount = proxyManager.removeExpired(BATCH_SIZE);
                log.info("Removed {} expired buckets", removedKeysCount);
            } while (removedKeysCount >= MIN_ACTUALLY_REMOVED_TO_PROCESS_NEW_BATCH);
        }

    }

}
