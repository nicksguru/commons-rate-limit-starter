package guru.nicks.commons.ratelimit.config;

import guru.nicks.commons.ratelimit.impl.RateLimitServiceImpl;
import guru.nicks.commons.ratelimit.service.RateLimitService;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.serialization.Mapper;
import io.github.bucket4j.redis.redisson.Bucket4jRedisson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.command.CommandAsyncExecutor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * Configures Redis-based Bucket4J storage using Redisson. See
 * <a href="https://bucket4j.com/8.13.1/toc.html#bucket4j-redis-redisson">Bucket4J manual</a> for details.
 * <p>
 * Redis handles bucket TTL automatically, eliminating the need for periodic cleanup jobs required with PostgreSQL.
 * <p>
 * Requires {@code commons-redis-starter} dependency to be present on classpath.
 */
@AutoConfiguration
@RequiredArgsConstructor
@Slf4j
public class CommonsRateLimitAutoConfiguration {

    /**
     * Creates {@link RateLimitService} bean if it's not already present.
     */
    @ConditionalOnMissingBean(RateLimitService.class)
    @Bean
    public RateLimitService rateLimitService(ProxyManager<String> proxyManager) {
        log.debug("Building {} bean", RateLimitService.class.getSimpleName());
        return new RateLimitServiceImpl(proxyManager);
    }

    /**
     * Creates {@link ProxyManager} bean if it's not already present. Uses Redisson for distributed rate limiting with
     * Redis backend.
     *
     * @return ProxyManager for Bucket4j
     * @throws IllegalStateException if RedissonClient is not available
     */
    @ConditionalOnMissingBean(ProxyManager.class)
    @Bean
    public ProxyManager<String> bucket4jProxyManager(RedissonClient redissonClient) {
        log.debug("Building {} bean", ProxyManager.class.getSimpleName());
        CommandAsyncExecutor commandExecutor = ((Redisson) redissonClient).getCommandExecutor();

        return Bucket4jRedisson
                .casBasedBuilder(commandExecutor)
                // thus buckets are not persisted in storage longer than time required to refill all consumed tokens
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
                // primary keys are strings, such as user IDs
                .keyMapper(Mapper.STRING)
                .build();
    }

    /*
    @ConditionalOnMissingBean(ProxyManager.class)
    @Bean
    public ProxyManager<String> bucket4jProxyManager(DataSource dataSource) {
        log.debug("Building {} bean", ProxyManager.class.getSimpleName());

        return Bucket4jPostgreSQL.selectForUpdateBasedBuilder(dataSource)
                .table("bucket4j")
                // primary keys are strings, such as user IDs
                .primaryKeyMapper(PrimaryKeyMapper.STRING)
                // thus buckets are not persisted in storage longer than time required to refill all consumed tokens
                // (provided that there's a cron job that deletes them, see @Scheduled)
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                        Duration.ofSeconds(60)))
                .build();
    }*/

    /**
     * Removes expired buckets from DB periodically.
     */
    //@Configuration(proxyBeanMethods = false)
    @RequiredArgsConstructor
    @Slf4j
    public static class ExpiresBucketCleanerCronJob {

        /**
         * At x:30 of every hour 'x': 00:00, 00:30, 01:00, 01:30, etc.
         */
        //public static final String CRON_EXPRESSION = "0 */30 * * * *";

        /**
         * How many buckets to remove at once (i.e. with one SQL statement).
         private static final int BATCH_SIZE = 200;
         */

        /**
         * During batch removal, some other buckets may expire. If this threshold is low, it's possible, under a very
         * high load on the rate limiting facility, that all buckets will never be removed due to eternal loop.
         private static final int MIN_ACTUALLY_REMOVED_TO_PROCESS_NEW_BATCH = 50;
         */

        /**
         * DB-powered {@link ProxyManager} created in outer class. Redis-powered one doesn't need or implement this
         * interface.
         private final ExpiredEntriesCleaner proxyManager;
         */

        /*@Scheduled(cron = CRON_EXPRESSION)
        public void scheduleFixedDelayTask() {
            int removedKeysCount;

            do {
                removedKeysCount = proxyManager.removeExpired(BATCH_SIZE);
                log.info("Removed {} expired buckets", removedKeysCount);
            } while (removedKeysCount >= MIN_ACTUALLY_REMOVED_TO_PROCESS_NEW_BATCH);
        }*/

    }

}
