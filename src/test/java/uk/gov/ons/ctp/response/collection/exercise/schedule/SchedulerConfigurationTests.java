package uk.gov.ons.ctp.response.collection.exercise.schedule;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import uk.gov.ons.ctp.response.collection.exercise.domain.CollectionExercise;
import uk.gov.ons.ctp.response.collection.exercise.domain.Event;
import uk.gov.ons.ctp.response.collection.exercise.service.EventService;

/** Class containing tests for SchedulerConfiguration */
@RunWith(MockitoJUnitRunner.class)
public class SchedulerConfigurationTests {
  private static final Timestamp EVENT_TIMESTAMP = new Timestamp(new Date().getTime());
  private static final EventService.Tag EVENT_TAG = EventService.Tag.go_live;
  @Mock private Scheduler scheduler;
  @Mock private EventService eventService;
  @InjectMocks private SchedulerConfiguration schedulerConfiguration;
  private Event event;
  private JobKey jobKey;

  /**
   * Generate a single test event with a random UUID (collection exercise also has random UUID)
   *
   * @return a test event
   */
  private static Event getTestEvent() {
    CollectionExercise collex = new CollectionExercise();
    collex.setId(UUID.randomUUID());

    Event event = new Event();

    event.setId(UUID.randomUUID());
    event.setCollectionExercise(collex);
    event.setTimestamp(EVENT_TIMESTAMP);
    event.setTag(EVENT_TAG.name());

    return event;
  }

  /** Setup method run before each test */
  @Before
  public void setUp() {
    this.event = getTestEvent();
    this.jobKey =
        JobKey.jobKey(
            SchedulerConfiguration.getJobKey(
                this.event.getCollectionExercise().getId(), this.event));
  }

  /**
   * Test of scheduling an event
   *
   * @throws SchedulerException thrown by quartz if there is a problem scheduling the event
   */
  @Test
  public void testScheduleEvent() throws SchedulerException {
    when(scheduler.checkExists(this.jobKey)).thenReturn(Boolean.FALSE);

    SchedulerConfiguration.scheduleEvent(this.scheduler, this.event);

    verify(scheduler, times(1)).scheduleJob(any(JobDetail.class), any(Trigger.class));
  }

  /**
   * Test of rescheduling an event
   *
   * @throws SchedulerException thrown by quartz if there is a problem rescheduling the event
   */
  @Test
  public void testRescheduleEvent() throws SchedulerException {
    when(scheduler.checkExists(this.jobKey)).thenReturn(Boolean.FALSE);

    SchedulerConfiguration.scheduleEvent(this.scheduler, this.event);

    when(scheduler.checkExists(this.jobKey)).thenReturn(Boolean.TRUE);

    SchedulerConfiguration.scheduleEvent(this.scheduler, event);

    verify(scheduler, times(2)).scheduleJob(any(JobDetail.class), any(Trigger.class));
  }

  /**
   * Test of unscheduling an event
   *
   * @throws SchedulerException thrown by quartz if there is a problem unscheduling the event
   */
  @Test
  public void testUnscheduleEvent() throws SchedulerException {
    when(scheduler.checkExists(this.jobKey)).thenReturn(Boolean.FALSE);

    SchedulerConfiguration.unscheduleEvent(this.scheduler, this.event);

    verify(scheduler, times(1)).deleteJob(this.jobKey);
  }

  /**
   * Test of scheduling outstanding events (those for which messageSent is null)
   *
   * @throws SchedulerException throw by quartz if error scheduling outstanding events
   */
  @Test
  public void testScheduleOutstandingEvents() throws SchedulerException {
    int numTestEvents = 10;
    List<Event> testEvents = getTestEventList(numTestEvents);

    when(this.eventService.getOutstandingEvents()).thenReturn(testEvents);

    this.schedulerConfiguration.scheduleOutstandingEvents(this.scheduler);

    verify(this.scheduler, times(numTestEvents))
        .scheduleJob(any(JobDetail.class), any(Trigger.class));
  }

  /**
   * Generate a list of test events
   *
   * @param numEvents the number of events to create
   * @return a list of events
   */
  private List<Event> getTestEventList(final Integer numEvents) {
    return Stream.generate(SchedulerConfigurationTests::getTestEvent)
        .limit(numEvents)
        .collect(Collectors.toList());
  }
}
