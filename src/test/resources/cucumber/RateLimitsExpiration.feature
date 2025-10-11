@security #@disabled
Feature: Rate limits expiration

  Background:
    Given expired rate limits cleanup batch size is 200

  Scenario: Bucket4j proxy manager is created with correct configuration
    When the bucket4j proxy manager is created
    Then the proxy manager should be properly configured

  Scenario: Expired buckets are cleaned up by the scheduled job
    Given a proxy manager with expired buckets
    When the scheduled cleanup job is executed
    Then expired buckets should be removed

  Scenario: Expired buckets cleanup stops when fewer buckets are removed than threshold
    Given a proxy manager with expired buckets
    And the number of expired buckets is less than the minimum threshold
    When the scheduled cleanup job is executed
    Then the cleanup process should stop after 2 batches
