package guru.nicks.cucumber;

import guru.nicks.cucumber.world.TextWorld;
import guru.nicks.exception.http.RateLimitExceededException;
import guru.nicks.ratelimit.RateLimit;
import guru.nicks.ratelimit.impl.RateLimitServiceImpl;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RequiredArgsConstructor
public class RateLimitServiceSteps {

    // DI
    private final TextWorld textWorld;
    @Mock
    RemoteBucketBuilder<String> remoteBucketBuilder;
    @Mock
    private ProxyManager<String> proxyManager;
    @Mock
    private BucketProxy bucketProxy;
    @Mock
    private ConsumptionProbe consumptionProbe;
    private AutoCloseable closeableMocks;
    private RateLimitServiceImpl rateLimitService;
    private RateLimit<String> rateLimit;
    private long remainingTokens;

    @Before
    public void beforeEachScenario() {
        closeableMocks = MockitoAnnotations.openMocks(this);
        rateLimitService = new RateLimitServiceImpl(proxyManager);

        // setup default mocks
        when(proxyManager.builder()).thenReturn(remoteBucketBuilder);
        when(remoteBucketBuilder.build(any(String.class), any(Supplier.class))).thenReturn(bucketProxy);
        when(bucketProxy.tryConsumeAndReturnRemaining(anyLong())).thenReturn(consumptionProbe);
    }

    @After
    public void afterEachScenario() throws Exception {
        closeableMocks.close();
    }

    @Given("a rate limit configuration with {int} tokens")
    public void aRateLimitConfigurationWithTokens(int tokens) {
        remainingTokens = tokens - 1; // after consumption

        rateLimit = new TestRateLimit();

        when(consumptionProbe.isConsumed()).thenReturn(tokens > 0);
        when(consumptionProbe.getRemainingTokens()).thenReturn(remainingTokens);
    }

    @Given("the time to wait for refill is {int} seconds")
    public void theTimeToWaitForRefillIsSeconds(int seconds) {
        long nanosToWaitForRefill = TimeUnit.SECONDS.toNanos(seconds);
        when(consumptionProbe.getNanosToWaitForRefill()).thenReturn(nanosToWaitForRefill);
    }

    @When("a request is made with key {string}")
    public void aRequestIsMadeWithKey(String key) {
        try {
            rateLimitService.throttle(key, rateLimit);
        } catch (Exception e) {
            textWorld.setLastException(e);
        }
    }

    @Then("the remaining tokens should be {int}")
    public void theRemainingTokensShouldBe(int expected) {
        assertThat(remainingTokens)
                .as("remaining tokens")
                .isEqualTo(expected);

        verify(bucketProxy).tryConsumeAndReturnRemaining(1L);
    }

    @Then("RateLimitExceededException exception should be thrown")
    public void rateLimitExceededExceptionShouldBeThrown() {
        assertThat(textWorld.getLastException())
                .as("lastException")
                .isNotNull();

        assertThat(textWorld.getLastException())
                .as("lastException")
                .isInstanceOf(RateLimitExceededException.class);
    }

    /**
     * Test implementation of RateLimit interface.
     */
    private static class TestRateLimit implements RateLimit<String> {

        @Nonnull
        @Override
        public BucketConfiguration getBucketConfiguration() {
            Bandwidth bandwidth = Bandwidth.builder()
                    .capacity(10)
                    // wait until the whole interval has elapsed, then refill all the tokens at once
                    .refillIntervally(10, Duration.ofMinutes(1))
                    .build();

            return BucketConfiguration.builder()
                    .addLimit(bandwidth)
                    .build();
        }

        @Nonnull
        @Override
        public String resolveBucketKey(@Nonnull String data) {
            return data;
        }

    }
}
