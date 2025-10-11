package guru.nicks.ratelimit.service;

import guru.nicks.exception.http.RateLimitExceededException;
import guru.nicks.ratelimit.RateLimit;

/**
 * Performs rate limiting a.k.a. throttling.
 */
public interface RateLimitService {

    /**
     * Applies rate limit. Using a servlet filter would introduce a noticeable delay (experiments show 280ms) because
     * each HTTP request has to be processed by the filter. To do that, the filter must read the whole HTTP request body
     * and then push it back, so controllers could read it again, which is slow.
     *
     * @param data      argument to {@link RateLimit#resolveBucketKey(Object)}
     * @param rateLimit tells the limit
     * @param <T>       {@code data} type
     * @throws RateLimitExceededException rate limit exceeded
     */
    <T> void throttle(T data, RateLimit<T> rateLimit);

}
