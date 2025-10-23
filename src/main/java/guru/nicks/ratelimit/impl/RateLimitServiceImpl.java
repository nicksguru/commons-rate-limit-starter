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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static guru.nicks.validation.dsl.ValiDsl.checkNotBlank;
import static guru.nicks.validation.dsl.ValiDsl.checkNotNull;

@RequiredArgsConstructor
@Slf4j
public class RateLimitServiceImpl implements RateLimitService {

    // DI
    private final ProxyManager<String> proxyManager;

    @Override
    public <T> void throttle(T data, RateLimit<T> rateLimit) {
        checkNotNull(rateLimit, "rate limit");
        String key = checkNotBlank(rateLimit.resolveBucketKey(data), "bucket key");

        // not 'debug' because key names may contain sensitive information, such as phone numbers
        if (log.isTraceEnabled()) {
            log.trace("Using bucket key '{}' for rate limiting", key);
        }

        BucketProxy bucket = proxyManager.builder()
                .build(key, rateLimit::getBucketConfiguration);
        ConsumptionProbe consumptionProbe = bucket.tryConsumeAndReturnRemaining(1L);

        // rate limit exceeded
        if (!consumptionProbe.isConsumed()) {
            long nanosUntilRefill = consumptionProbe.getNanosToWaitForRefill();
            long secondsUntilRefill = TimeUnit.NANOSECONDS.toSeconds(nanosUntilRefill);

            // ensure min. 1s in response header (0 would mean 'can retry immediately')
            if ((nanosUntilRefill > 0) && (secondsUntilRefill == 0)) {
                log.warn("Rate limit wait time less than 1s - rounding up to 1s for HTTP response header");
                secondsUntilRefill = 1L;
            }

            throw new RateLimitExceededException(Map.of(HttpHeaders.RETRY_AFTER, secondsUntilRefill));
        }

        // not 'debug' because key names may contain sensitive information, such as phone numbers
        if (log.isTraceEnabled()) {
            log.trace("Rate bucket has {} remaining tokens for key '{}'", consumptionProbe.getRemainingTokens(), key);
        }
    }

}
