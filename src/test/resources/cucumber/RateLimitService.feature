@security #@disabled
Feature: Rate Limit Service
  Rate limit service should limit requests based on configured limits

  Scenario: Successful request when rate limit is not exceeded
    Given a rate limit configuration with 5 tokens
    When a request is made with key "test-key"
    Then no exception should be thrown
    And the remaining tokens should be 4

  Scenario: Request is rejected when rate limit is exceeded
    Given a rate limit configuration with 0 tokens
    And the time to wait for refill is 60 seconds
    When a request is made with key "test-key"
    Then RateLimitExceededException exception should be thrown

  Scenario Outline: Different keys should have separate rate limits
    Given a rate limit configuration with <tokens> tokens
    When a request is made with key "<key>"
    Then the remaining tokens should be <remaining>
    Examples:
      | tokens | key       | remaining |
      | 5      | user-1    | 4         |
      | 10     | user-2    | 9         |
      | 3      | admin-key | 2         |
