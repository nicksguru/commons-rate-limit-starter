package guru.nicks.ratelimit;

import guru.nicks.ratelimit.service.RateLimitService;
import guru.nicks.utils.PhoneNumberUtils;

import io.github.bucket4j.BucketConfiguration;

/**
 * Bucket configuration for rate limiting. Remember to normalize the input data (for example, phone numbers with
 * {@link PhoneNumberUtils#normalizeInternationalPhoneNumber(String)}) to prevent cosmetically (but not logically)
 * different values from being routed to different bucket keys.
 *
 * @param <T> input data type
 * @see RateLimitService
 */
public interface RateLimit<T> {

    /**
     * Defines how to create a new bucket.
     *
     * @return bucket configuration
     */
    BucketConfiguration getBucketConfiguration();

    /**
     * Defines the key (unique bucket name) where the number of attempts for the given input data is stored.
     *
     * @param data for example, a phone number or an object where the phone number is a property (remember to normalize
     *             it before calling this method)
     * @return bucket key
     */
    String resolveBucketKey(T data);

}
