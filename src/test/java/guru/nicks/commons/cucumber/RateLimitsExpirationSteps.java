package guru.nicks.commons.cucumber;

import guru.nicks.commons.ratelimit.config.CommonsRateLimitAutoConfiguration;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.bucket4j.distributed.proxy.ExpiredEntriesCleaner;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Step definitions for testing {@link CommonsRateLimitAutoConfiguration}.
 */
public class RateLimitsExpirationSteps {

    @Mock
    private DataSource dataSource;
    @Mock
    private ExpiredEntriesCleaner expiredEntriesCleaner;
    @Captor
    private ArgumentCaptor<Integer> batchSizeCaptor;
    private AutoCloseable closeableMocks;

    private CommonsRateLimitAutoConfiguration.ExpiresBucketCleanerCronJob cleanerCronJob;
    private ProxyManager<String> proxyManager;
    private int batchSize;

    @Before
    public void beforeEachScenario() {
        closeableMocks = MockitoAnnotations.openMocks(this);
    }

    @After
    public void afterEachScenario() throws Exception {
        closeableMocks.close();
    }

    @Given("expired rate limits cleanup batch size is {int}")
    public void expiredRateLimitsCleanupBatchSizeIs(int batchSize) {
        this.batchSize = batchSize;
    }

    @Given("a proxy manager with expired buckets")
    public void aProxyManagerWithExpiredBuckets() {
        cleanerCronJob = new CommonsRateLimitAutoConfiguration.ExpiresBucketCleanerCronJob(expiredEntriesCleaner);

        // default behavior: return batch size to indicate full batch was processed and nothing remains to process
        when(expiredEntriesCleaner.removeExpired(batchSizeCaptor.capture()))
                .thenReturn(batchSize)
                .thenReturn(0);
    }

    @Given("the number of expired buckets is less than the minimum threshold")
    public void theNumberOfExpiredBucketsIsLessThanTheMinimumThreshold() {
        // first call returns batch size, second call returns less than the threshold
        when(expiredEntriesCleaner.removeExpired(batchSizeCaptor.capture()))
                .thenReturn(batchSize)
                // less than MIN_ACTUALLY_REMOVED_TO_PROCESS_NEW_BATCH (50)
                .thenReturn(20);
    }

    @When("the bucket4j proxy manager is created")
    public void theBucket4jProxyManagerIsCreated() {
        var rateLimitConfig = new CommonsRateLimitAutoConfiguration();
        proxyManager = rateLimitConfig.bucket4jProxyManager(dataSource);
    }

    @When("the scheduled cleanup job is executed")
    public void theScheduledCleanupJobIsExecuted() {
        cleanerCronJob.scheduleFixedDelayTask();
    }

    @Then("the proxy manager should be properly configured")
    public void theProxyManagerShouldBeProperlyConfigured() {
        assertThat(proxyManager)
                .as("proxyManager")
                .isNotNull();
    }

    @Then("expired buckets should be removed")
    public void expiredBucketsShouldBeRemoved() {
        verify(expiredEntriesCleaner, times(2)).removeExpired(batchSize);

        assertThat(batchSizeCaptor.getValue())
                .as("batchSize")
                .isEqualTo(batchSize);
    }

    @Then("the cleanup process should stop after {int} batches")
    public void theCleanupProcessShouldStopAfterBatches(int batchCount) {
        verify(expiredEntriesCleaner, times(batchCount)).removeExpired(batchSize);
    }

}
