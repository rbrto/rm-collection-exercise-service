package uk.gov.ons.ctp.response.collection.exercise.validation;

import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.gov.ons.ctp.response.collection.exercise.service.SampleService;

/**
 * Schedule Validation of sample units in INIT state checking Collection Instruments exist for each
 * sample unit.
 */
@Component
public class ValidationScheduler {
  private static final String VALIDATION_GLOBAL_LOCK_NAME = "SampleValidationCollexLock";

  private SampleService sampleService;
  private RedissonClient redissonClient;

  @Autowired
  public ValidationScheduler(SampleService sampleService, RedissonClient redissonClient) {
    this.sampleService = sampleService;
    this.redissonClient = redissonClient;
  }

  /** Carry out scheduled validation according to configured fixed delay. */
  @Scheduled(fixedDelayString = "#{appConfig.schedules.validationScheduleDelayMilliSeconds}")
  public void scheduleValidation() {
    RLock lock = redissonClient.getFairLock(VALIDATION_GLOBAL_LOCK_NAME);

    // Get a lock. Automatically unlock after a certain amount of time to prevent issues
    // when lock holder crashes or Redis crashes causing permanent lockout
    try {
      if (lock.tryLock(1, TimeUnit.HOURS)) {
        try {
          sampleService.validateSampleUnits();
        } finally {
          lock.unlock();
        }
      }
    } catch (InterruptedException e) {
      // Ignored
    }
  }
}
