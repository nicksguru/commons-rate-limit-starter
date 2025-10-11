package guru.nicks.ratelimit.impl;

import guru.nicks.exception.http.RateLimitExceededException;
import guru.nicks.ratelimit.RateLimit;
import guru.nicks.ratelimit.service.RateLimitService;

import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitServiceImpl implements RateLimitService {

    // DI
    private final ProxyManager<String> proxyManager;

    @Override
    public <T> void throttle(T data, RateLimit<T> rateLimit) {
        String key = rateLimit.resolveBucketKey(data);
        // not 'debug' because key names may contain sensitive information, such as phone numbers
        if (log.isTraceEnabled()) {
            log.trace("Using counter key '{}' for rate limiting", key);
        }

        BucketProxy bucket = proxyManager.builder()
                .build(key, rateLimit::getBucketConfiguration);
        ConsumptionProbe consumptionProbe = bucket.tryConsumeAndReturnRemaining(1L);

        // rate limit exceeded
        if (!consumptionProbe.isConsumed()) {
            long secondsUntilRefill = TimeUnit.NANOSECONDS.toSeconds(consumptionProbe.getNanosToWaitForRefill());
            throw new RateLimitExceededException(Map.of(HttpHeaders.RETRY_AFTER, secondsUntilRefill));
        }

        // not 'debug' because key names may contain sensitive information, such as phone numbers
        if (log.isTraceEnabled()) {
            log.trace("Rate bucket has {} remaining tokens for key '{}'", consumptionProbe.getRemainingTokens(), key);
        }
    }

}
