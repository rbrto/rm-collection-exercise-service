package uk.gov.ons.ctp.response.collection.exercise.config;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import lombok.Data;
import net.sourceforge.cobertura.CoverageIgnore;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import uk.gov.ons.tools.rabbit.Rabbitmq;

/**
 * The apps main holder for centralised configuration read from application.yml or environment
 * variables.
 */
@CoverageIgnore
@Configuration
@ConfigurationProperties
@Data
public class AppConfig implements AsyncConfigurer {
  private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

  private ActionSvc actionSvc;
  private SampleSvc sampleSvc;
  private SurveySvc surveySvc;
  private CollectionInstrumentSvc collectionInstrumentSvc;
  private PartySvc partySvc;
  private RedissonConfig redissonConfig;
  private ScheduleSettings schedules;
  private SwaggerSettings swaggerSettings;
  private Rabbitmq rabbitmq;
  private Logging logging;

  @Bean
  public CacheManager cacheManager() {
    return new ConcurrentMapCacheManager("collectioninstruments", "actionplans");
  }

  @Override
  public Executor getAsyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(50);
    executor.setMaxPoolSize(100);
    executor.setQueueCapacity(500000);
    executor.setThreadNamePrefix("MyExecutor-");
    executor.initialize();
    return executor;
  }

  @Override
  public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
    return new AsyncUncaughtExceptionHandler() {
      @Override
      public void handleUncaughtException(Throwable throwable, Method method, Object... objects) {
        log.error("THIS IS THE WORST THING EVER", throwable);
      }
    };
  }
}
