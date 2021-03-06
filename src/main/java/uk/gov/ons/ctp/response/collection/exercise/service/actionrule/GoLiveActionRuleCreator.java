package uk.gov.ons.ctp.response.collection.exercise.service.actionrule;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.response.action.representation.ActionPlanDTO;
import uk.gov.ons.ctp.response.action.representation.ActionType;
import uk.gov.ons.ctp.response.collection.exercise.client.ActionSvcClient;
import uk.gov.ons.ctp.response.collection.exercise.client.SurveySvcClient;
import uk.gov.ons.ctp.response.collection.exercise.domain.CollectionExercise;
import uk.gov.ons.ctp.response.collection.exercise.domain.Event;
import uk.gov.ons.ctp.response.collection.exercise.service.ActionRuleCreator;
import uk.gov.ons.ctp.response.collection.exercise.service.EventService.Tag;
import uk.gov.ons.response.survey.representation.SurveyDTO;

@Component
public final class GoLiveActionRuleCreator implements ActionRuleCreator {
  private final ActionSvcClient actionSvcClient;
  private final SurveySvcClient surveyService;

  public GoLiveActionRuleCreator(
      final ActionSvcClient actionSvcClient, final SurveySvcClient surveyService) {
    this.actionSvcClient = actionSvcClient;
    this.surveyService = surveyService;
  }

  @Override
  public void execute(final Event collectionExerciseEvent) throws CTPException {

    if (!isGoLive(collectionExerciseEvent)) {
      return;
    }

    final CollectionExercise collectionExercise = collectionExerciseEvent.getCollectionExercise();

    final SurveyDTO survey = surveyService.getSurveyForCollectionExercise(collectionExercise);

    if (survey.getSurveyType() != SurveyDTO.SurveyType.Business) {
      return;
    }

    final Instant instant = Instant.ofEpochMilli(collectionExerciseEvent.getTimestamp().getTime());
    final OffsetDateTime offsetDateTime = OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());

    final ActionPlanDTO actionPlan =
        actionSvcClient.getActionPlanBySelectorsBusiness(
            collectionExercise.getId().toString(), true);

    actionSvcClient.createActionRule(
        survey.getShortName() + "NOTE",
        survey.getShortName() + " Notification Email " + collectionExercise.getExerciseRef(),
        ActionType.BSNE,
        offsetDateTime,
        3,
        actionPlan.getId());
  }

  private boolean isGoLive(final Event collectionExerciseEvent) {
    return Tag.go_live.hasName(collectionExerciseEvent.getTag());
  }
}
