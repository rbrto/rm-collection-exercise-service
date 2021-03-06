package uk.gov.ons.ctp.response.collection.exercise.service;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.common.state.StateTransitionManager;
import uk.gov.ons.ctp.response.action.representation.ActionPlanDTO;
import uk.gov.ons.ctp.response.collection.exercise.client.ActionSvcClient;
import uk.gov.ons.ctp.response.collection.exercise.client.CollectionInstrumentSvcClient;
import uk.gov.ons.ctp.response.collection.exercise.client.SampleSvcClient;
import uk.gov.ons.ctp.response.collection.exercise.client.SurveySvcClient;
import uk.gov.ons.ctp.response.collection.exercise.domain.CaseType;
import uk.gov.ons.ctp.response.collection.exercise.domain.CaseTypeDefault;
import uk.gov.ons.ctp.response.collection.exercise.domain.CaseTypeOverride;
import uk.gov.ons.ctp.response.collection.exercise.domain.CollectionExercise;
import uk.gov.ons.ctp.response.collection.exercise.domain.SampleLink;
import uk.gov.ons.ctp.response.collection.exercise.repository.CaseTypeDefaultRepository;
import uk.gov.ons.ctp.response.collection.exercise.repository.CaseTypeOverrideRepository;
import uk.gov.ons.ctp.response.collection.exercise.repository.CollectionExerciseRepository;
import uk.gov.ons.ctp.response.collection.exercise.repository.SampleLinkRepository;
import uk.gov.ons.ctp.response.collection.exercise.representation.CollectionExerciseDTO;
import uk.gov.ons.ctp.response.collection.exercise.representation.CollectionExerciseDTO.CollectionExerciseState;
import uk.gov.ons.ctp.response.sample.representation.SampleSummaryDTO;
import uk.gov.ons.response.survey.representation.SurveyDTO;

/** The implementation of the SampleService */
@Service
public class CollectionExerciseService {
  private static final Logger log = LoggerFactory.getLogger(CollectionExerciseService.class);

  private final CaseTypeDefaultRepository caseTypeDefaultRepo;

  private final CaseTypeOverrideRepository caseTypeOverrideRepo;

  private final CollectionExerciseRepository collectRepo;

  private final ActionSvcClient actionSvcClient;

  private final CollectionInstrumentSvcClient collectionInstrumentSvcClient;

  private final SampleLinkRepository sampleLinkRepository;

  private final SampleSvcClient sampleSvcClient;

  private final SurveySvcClient surveyService;

  private final RabbitTemplate rabbitTemplate;

  private final StateTransitionManager<
          CollectionExerciseDTO.CollectionExerciseState,
          CollectionExerciseDTO.CollectionExerciseEvent>
      collectionExerciseTransitionState;

  @Autowired
  public CollectionExerciseService(
      CaseTypeDefaultRepository caseTypeDefaultRepo,
      CaseTypeOverrideRepository caseTypeOverrideRepo,
      CollectionExerciseRepository collectRepo,
      SampleLinkRepository sampleLinkRepository,
      ActionSvcClient actionSvcClient,
      CollectionInstrumentSvcClient collectionInstrumentSvcClient,
      SampleSvcClient sampleSvcClient,
      SurveySvcClient surveyService,
      @Qualifier("collexTransitionTemplate") RabbitTemplate rabbitTemplate,
      @Qualifier("collectionExercise")
          StateTransitionManager<
                  CollectionExerciseDTO.CollectionExerciseState,
                  CollectionExerciseDTO.CollectionExerciseEvent>
              collectionExerciseTransitionState) {
    this.caseTypeOverrideRepo = caseTypeOverrideRepo;
    this.caseTypeDefaultRepo = caseTypeDefaultRepo;
    this.collectRepo = collectRepo;
    this.sampleLinkRepository = sampleLinkRepository;
    this.actionSvcClient = actionSvcClient;
    this.collectionInstrumentSvcClient = collectionInstrumentSvcClient;
    this.surveyService = surveyService;
    this.sampleSvcClient = sampleSvcClient;
    this.rabbitTemplate = rabbitTemplate;
    this.collectionExerciseTransitionState = collectionExerciseTransitionState;
  }

  /**
   * Find a list of collection exercises associated to a survey from the Collection Exercise Service
   *
   * @param survey the survey for which to find collection exercises
   * @return the associated collection exercises.
   */
  public List<CollectionExercise> findCollectionExercisesForSurvey(SurveyDTO survey) {
    return this.collectRepo.findBySurveyId(UUID.fromString(survey.getId()));
  }

  /**
   * find a list of all sample summary linked to a collection exercise
   *
   * @param id the collection exercise Id to find the linked sample summaries for
   * @return list of linked sample summary
   */
  public List<SampleLink> findLinkedSampleSummaries(UUID id) {
    return sampleLinkRepository.findByCollectionExerciseId(id);
  }

  /**
   * Find all Collection Exercises
   *
   * @return a list of all Collection Exercises
   */
  public List<CollectionExercise> findAllCollectionExercise() {
    return collectRepo.findAll();
  }

  public List<CollectionExercise> findCollectionExercisesBySurveyIdAndState(
      UUID surveyId, CollectionExerciseState state) {
    return collectRepo.findBySurveyIdAndState(surveyId, state);
  }

  /**
   * Find a collection exercise associated to a collection exercise Id from the Collection Exercise
   * Service
   *
   * @param id the collection exercise Id for which to find collection exercise
   * @throws CTPException if collection exercise not found
   * @return the associated collection exercise.
   */
  public CollectionExercise findCollectionExercise(UUID id) {

    return collectRepo.findOneById(id);
  }

  /**
   * Find a collection exercise from a survey ref (e.g. 221) and a collection exercise ref (e.g.
   * 201808)
   *
   * @param surveyRef the survey ref
   * @param exerciseRef the collection exercise ref
   * @return the specified collection exercise or null if not found
   */
  public CollectionExercise findCollectionExercise(String surveyRef, String exerciseRef) {
    CollectionExercise collex = null;
    SurveyDTO survey = this.surveyService.findSurveyByRef(surveyRef);

    if (survey != null) {
      collex = findCollectionExercise(exerciseRef, survey);
    }

    return collex;
  }

  /**
   * Gets collection exercise with given exerciseRef and survey (should be no more than 1)
   *
   * @param exerciseRef the exerciseRef (period) of the collection exercise
   * @param survey the survey the collection exercise is associated with
   * @return the collection exercise if it exists, null otherwise
   */
  public CollectionExercise findCollectionExercise(String exerciseRef, SurveyDTO survey) {
    List<CollectionExercise> existing =
        this.collectRepo.findByExerciseRefAndSurveyId(exerciseRef, UUID.fromString(survey.getId()));

    switch (existing.size()) {
      case 0:
        return null;
      default:
        return existing.get(0);
    }
  }

  /**
   * Gets collection exercise with given exerciseRef and survey uuid (should be no more than 1)
   *
   * @param exerciseRef the exerciseRef (period) of the collection exercise
   * @param surveyId the uuid of the survey the collection exercise is associated with
   * @return the collection exercise if it exists, null otherwise
   */
  public CollectionExercise findCollectionExercise(final String exerciseRef, final UUID surveyId) {
    List<CollectionExercise> existing =
        this.collectRepo.findByExerciseRefAndSurveyId(exerciseRef, surveyId);

    switch (existing.size()) {
      case 0:
        return null;
      default:
        return existing.get(0);
    }
  }

  /**
   * Find case types associated to a collection exercise from the Collection Exercise Service
   *
   * @param collectionExercise the collection exercise for which to find case types
   * @return the associated case type DTOs.
   */
  public Collection<CaseType> getCaseTypesList(CollectionExercise collectionExercise) {

    List<CaseTypeDefault> caseTypeDefaultList =
        caseTypeDefaultRepo.findBySurveyId(collectionExercise.getSurveyId());

    List<CaseTypeOverride> caseTypeOverrideList =
        caseTypeOverrideRepo.findByExerciseFK(collectionExercise.getExercisePK());

    return createCaseTypeList(caseTypeDefaultList, caseTypeOverrideList);
  }

  /**
   * Creates a Collection of CaseTypes
   *
   * @param caseTypeDefaultList List of caseTypeDefaults
   * @param caseTypeOverrideList List of caseTypeOverrides
   * @return Collection<CaseType> Collection of CaseTypes
   */
  public Collection<CaseType> createCaseTypeList(
      List<? extends CaseType> caseTypeDefaultList, List<? extends CaseType> caseTypeOverrideList) {

    Map<String, CaseType> defaultMap = new HashMap<>();

    for (CaseType caseTypeDefault : caseTypeDefaultList) {
      defaultMap.put(caseTypeDefault.getSampleUnitTypeFK(), caseTypeDefault);
    }

    for (CaseType caseTypeOverride : caseTypeOverrideList) {
      defaultMap.put(caseTypeOverride.getSampleUnitTypeFK(), caseTypeOverride);
    }

    return defaultMap.values();
  }

  /**
   * Delete existing SampleSummary links for input CollectionExercise then link all SampleSummaries
   * in list to CollectionExercise
   *
   * @param collectionExerciseId the Id of the CollectionExercise to link to
   * @param sampleSummaryIds the list of Ids of the SampleSummaries to be linked
   * @return linkedSummaries the list of CollectionExercises and the linked SampleSummaries
   */
  @Transactional
  public List<SampleLink> linkSampleSummaryToCollectionExercise(
      UUID collectionExerciseId, List<UUID> sampleSummaryIds) throws CTPException {
    sampleLinkRepository.deleteByCollectionExerciseId(collectionExerciseId);
    List<SampleLink> linkedSummaries = new ArrayList<>();
    for (UUID summaryId : sampleSummaryIds) {
      linkedSummaries.add(createLink(summaryId, collectionExerciseId));
    }

    transitionScheduleCollectionExerciseToReadyToReview(collectionExerciseId);

    return linkedSummaries;
  }

  /**
   * Delete SampleSummary link
   *
   * @param sampleSummaryId a sample summary uuid
   * @param collectionExerciseId a collection exercise uuid
   * @throws CTPException thrown if transition fails
   */
  @Transactional
  public void removeSampleSummaryLink(final UUID sampleSummaryId, final UUID collectionExerciseId)
      throws CTPException {
    sampleLinkRepository.deleteBySampleSummaryIdAndCollectionExerciseId(
        sampleSummaryId, collectionExerciseId);

    List<SampleLink> sampleLinks =
        this.sampleLinkRepository.findByCollectionExerciseId(collectionExerciseId);

    if (sampleLinks.size() == 0) {
      transitionCollectionExercise(
          collectionExerciseId, CollectionExerciseDTO.CollectionExerciseEvent.CI_SAMPLE_DELETED);
    }
  }

  /**
   * Sets the values in a supplied collection exercise from a supplied DTO. WARNING: Mutates
   * collection exercise
   *
   * @param collex the dto containing the data
   * @param collectionExercise the collection exercise to apply the value from the dto to
   */
  private void setCollectionExerciseFromDto(
      CollectionExerciseDTO collex, CollectionExercise collectionExercise) {
    collectionExercise.setUserDescription(collex.getUserDescription());
    collectionExercise.setExerciseRef(collex.getExerciseRef());
    collectionExercise.setSurveyId(UUID.fromString(collex.getSurveyId()));

    // In the strictest sense, some of these dates are mandatory fields for collection exercises.
    // However as they
    // are not supplied at creation time, but later as "events" we will allow them to be null
    if (collex.getScheduledStartDateTime() != null) {
      collectionExercise.setScheduledStartDateTime(
          new Timestamp(collex.getScheduledStartDateTime().getTime()));
    }
    if (collex.getScheduledEndDateTime() != null) {
      collectionExercise.setScheduledEndDateTime(
          new Timestamp(collex.getScheduledEndDateTime().getTime()));
    }
    if (collex.getScheduledExecutionDateTime() != null) {
      collectionExercise.setScheduledExecutionDateTime(
          new Timestamp(collex.getScheduledExecutionDateTime().getTime()));
    }
    if (collex.getActualExecutionDateTime() != null) {
      collectionExercise.setActualExecutionDateTime(
          new Timestamp(collex.getActualExecutionDateTime().getTime()));
    }
    if (collex.getActualPublishDateTime() != null) {
      collectionExercise.setActualPublishDateTime(
          new Timestamp(collex.getActualPublishDateTime().getTime()));
    }
  }

  /**
   * Create collection exercise This will also create the required action plans and casetypeoverride
   *
   * @param collex the data to create the collection exercise from
   * @param survey representation of the survey for the given collection exercise
   * @return created collection exercise
   */
  @Transactional
  public CollectionExercise createCollectionExercise(
      CollectionExerciseDTO collex, SurveyDTO survey) {
    log.with("survey_ref", survey.getSurveyRef())
        .with("exercise_ref", collex.getExerciseRef())
        .debug("Creating collection exercise");
    CollectionExercise collectionExercise = newCollectionExerciseFromDTO(collex);
    // Save collection exercise before creating action plans because we need the exercisepk
    collectionExercise = this.collectRepo.saveAndFlush(collectionExercise);
    createActionPlans(collectionExercise, survey);
    log.with("collection_exercise_id", collectionExercise.getId())
        .debug("Successfully created collection exercise");
    return collectionExercise;
  }

  /**
   * Create and populate details of collection exercise.
   *
   * @param collex collection exercise
   * @return collection exercise with details
   */
  private CollectionExercise newCollectionExerciseFromDTO(CollectionExerciseDTO collex) {
    log.debug("Create new collection exercise from DTO");
    CollectionExercise collectionExercise = new CollectionExercise();
    setCollectionExerciseFromDto(collex, collectionExercise);
    collectionExercise.setState(CollectionExerciseDTO.CollectionExerciseState.CREATED);
    collectionExercise.setCreated(new Timestamp(new Date().getTime()));
    collectionExercise.setId(UUID.randomUUID());
    log.with("collection_exercise_id", collectionExercise.getId())
        .debug("Successfully created collection exercise from DTO");
    return collectionExercise;
  }

  /**
   * Create required action plans
   *
   * @param collectionExercise Collection Exercise
   * @param survey SurveyDTO representing survey of collection exercise
   */
  private void createActionPlans(CollectionExercise collectionExercise, SurveyDTO survey) {
    log.with("collection_exercise_id", collectionExercise.getId())
        .with("survey_id", survey.getId())
        .debug("Creating action plans for exercise");

    switch (survey.getSurveyType()) {
      case Business:
        createDefaultActionPlan(survey, "B");
        createDefaultActionPlan(survey, "BI");
        createOverrideActionPlan(collectionExercise, survey, "B");
        createOverrideActionPlan(collectionExercise, survey, "BI");
        break;

      case Social:
        createDefaultActionPlan(survey, "H");
        createOverrideActionPlan(collectionExercise, survey, "H");
        break;

      case Census:
      default:
        throw new RuntimeException("Census surveys not supported... yet!");
    }

    log.with("collection_exercise_id", collectionExercise.getId())
        .with("survey_id", survey.getId())
        .debug("Successfully created action plans for exercise");
  }

  /**
   * Create default action plan for collection exercise and case type if not already exists
   *
   * @param survey DTO Representation of survey
   * @param sampleUnitType Sample Unit Type i.e. (B, H, HI)
   */
  private void createDefaultActionPlan(SurveyDTO survey, String sampleUnitType) {
    log.with("sample_unit_type", sampleUnitType)
        .with("survey_id", survey.getId())
        .debug("Creating default action plan");

    // If a casetypedefault already exists for this survey/sampleUnitType do nothing
    CaseTypeDefault existingCaseTypeDefault =
        caseTypeDefaultRepo.findTopBySurveyIdAndSampleUnitTypeFK(
            UUID.fromString(survey.getId()), sampleUnitType);
    if (existingCaseTypeDefault != null) {
      log.with("sample_unit_type", sampleUnitType)
          .with("survey_id", survey.getId())
          .debug("Default action plan already exists");
      return;
    }

    // Create new action plan and associated casetypedefault
    String shortName = survey.getShortName();
    String name = String.format("%s %s", shortName, sampleUnitType);
    String description = String.format("%s %s Case", shortName, sampleUnitType);
    ActionPlanDTO actionPlan = actionSvcClient.createActionPlan(name, description, null);
    createCaseTypeDefault(survey, sampleUnitType, actionPlan);
    log.with("sample_unit_type", sampleUnitType)
        .with("survey_id", survey.getId())
        .with("action_plan_id", actionPlan.getId())
        .debug("Successfully created default action plan");
  }

  /**
   * Create case type default for action plan, survey and sample unit type if not already exists
   *
   * @param survey DTO Representation of survey
   * @param sampleUnitType Sample Unit Type
   * @param actionPlan DTO Representation of action plan
   */
  private void createCaseTypeDefault(
      SurveyDTO survey, String sampleUnitType, ActionPlanDTO actionPlan) {
    log.with("sample_unit_type", sampleUnitType)
        .with("survey_id", survey.getId())
        .with("action_plan_id", actionPlan.getId())
        .debug("Creating case type default");
    CaseTypeDefault caseTypeDefault = new CaseTypeDefault();
    caseTypeDefault.setSurveyId(UUID.fromString(survey.getId()));
    caseTypeDefault.setSampleUnitTypeFK(sampleUnitType);
    caseTypeDefault.setActionPlanId(actionPlan.getId());

    this.caseTypeDefaultRepo.saveAndFlush(caseTypeDefault);
    log.with("sample_unit_type", sampleUnitType)
        .with("survey_id", survey.getId())
        .with("action_plan_id", actionPlan.getId())
        .debug("Successfully created case type default");
  }

  /**
   * Create action plan for given collection exercise and case type if not already exists
   *
   * @param survey DTO Representation of survey for collection exercise
   * @param collectionExercise Collection Exercise
   * @param sampleUnitType Sample Unit Type i.e. (B, H, HI)
   */
  private void createOverrideActionPlan(
      CollectionExercise collectionExercise, SurveyDTO survey, String sampleUnitType) {
    log.with("sample_unit_type", sampleUnitType)
        .with("survey_id", survey.getId())
        .with("collection_exercise_id", collectionExercise.getId())
        .debug("Creating override action plan");

    // If a casetypeoverride already exists for this exercise/sampleUnitType do nothing
    CaseTypeOverride existingCaseTypeOverride =
        caseTypeOverrideRepo.findTopByExerciseFKAndSampleUnitTypeFK(
            collectionExercise.getExercisePK(), sampleUnitType);
    if (existingCaseTypeOverride != null) {
      log.with("sample_unit_type", sampleUnitType)
          .with("survey_id", survey.getId())
          .with("collection_exercise_id", collectionExercise.getId())
          .debug("Override action plan already exists");
      return;
    }

    // Create action plan with appropriate name and description
    HashMap<String, String> selectors = new HashMap<>();
    selectors.put("collectionExerciseId", collectionExercise.getId().toString());
    if (!"H".equals(sampleUnitType) && !"HI".equals(sampleUnitType)) {
      String activeEnrolment = Boolean.toString("BI".equals(sampleUnitType));
      selectors.put("activeEnrolment", activeEnrolment);
    }
    String shortName = survey.getShortName();
    String exerciseRef = collectionExercise.getExerciseRef();
    String name = String.format("%s %s %s", shortName, sampleUnitType, exerciseRef);
    String description = String.format("%s %s Case %s", shortName, sampleUnitType, exerciseRef);
    ActionPlanDTO actionPlan = actionSvcClient.createActionPlan(name, description, selectors);

    // Create casetypeoverride linking collection exercise and sample unit type to the action plan
    createCaseTypeOverride(collectionExercise, sampleUnitType, actionPlan);
    log.with("sample_unit_type", sampleUnitType)
        .with("action_plan_id", actionPlan.getId())
        .with("collection_exercise_id", collectionExercise.getId())
        .debug("Successfully created override action plan");
  }

  /**
   * Create case type override for given action plan and collection exercise
   *
   * @param collectionExercise representation of collection exercise
   * @param sampleUnitType Sample unit type i.e. (B, H, HI)
   * @param actionPlan the newly created action plan
   * @throws DataAccessException if caseTypeOverride fails to save to database
   */
  private void createCaseTypeOverride(
      CollectionExercise collectionExercise, String sampleUnitType, ActionPlanDTO actionPlan)
      throws DataAccessException {
    log.with("sample_unit_type", sampleUnitType)
        .with("action_plan_id", actionPlan.getId())
        .with("collection_exercise_id", collectionExercise.getId())
        .debug("Creating case type override");
    CaseTypeOverride caseTypeOverride = new CaseTypeOverride();
    caseTypeOverride.setExerciseFK(collectionExercise.getExercisePK());
    caseTypeOverride.setSampleUnitTypeFK(sampleUnitType);
    caseTypeOverride.setActionPlanId(actionPlan.getId());
    this.caseTypeOverrideRepo.saveAndFlush(caseTypeOverride);
    log.with("sample_unit_type", sampleUnitType)
        .with("action_plan_id", actionPlan.getId())
        .with("collection_exercise_id", collectionExercise.getId())
        .debug("Successfully created case type override");
  }

  /**
   * Patch a collection exercise
   *
   * @param id the id of the collection exercise to patch
   * @param patchData the patch data
   * @return the patched CollectionExercise object
   * @throws CTPException thrown if error occurs
   */
  public CollectionExercise patchCollectionExercise(UUID id, CollectionExerciseDTO patchData)
      throws CTPException {
    CollectionExercise collex = findCollectionExercise(id);

    if (collex == null) {
      throw new CTPException(
          CTPException.Fault.RESOURCE_NOT_FOUND,
          String.format("Collection exercise %s not found", id));
    } else {
      String proposedPeriod =
          patchData.getExerciseRef() == null ? collex.getExerciseRef() : patchData.getExerciseRef();
      UUID proposedSurvey =
          patchData.getSurveyId() == null
              ? collex.getSurveyId()
              : UUID.fromString(patchData.getSurveyId());

      // If period/survey not supplied in patchData then this call will trivially return
      validateUniqueness(collex, proposedPeriod, proposedSurvey);

      if (!StringUtils.isBlank(patchData.getSurveyId())) {
        UUID surveyId = UUID.fromString(patchData.getSurveyId());

        SurveyDTO survey = this.surveyService.findSurvey(surveyId);

        if (survey == null) {
          throw new CTPException(
              CTPException.Fault.BAD_REQUEST, String.format("Survey %s does not exist", surveyId));
        } else {
          collex.setSurveyId(surveyId);
        }
      }

      if (!StringUtils.isBlank(patchData.getExerciseRef())) {
        collex.setExerciseRef(patchData.getExerciseRef());
      }
      if (!StringUtils.isBlank(patchData.getUserDescription())) {
        collex.setUserDescription(patchData.getUserDescription());
      }
      if (patchData.getScheduledStartDateTime() != null) {
        collex.setScheduledStartDateTime(
            new Timestamp(patchData.getScheduledStartDateTime().getTime()));
      }

      collex.setUpdated(new Timestamp(new Date().getTime()));

      return updateCollectionExercise(collex);
    }
  }

  /**
   * This method checks whether the supplied CollectionExercise (existing) can change it's period to
   * candidatePeriod and it's survey to candidateSurvey without breaching the uniqueness constraint
   * on those fields
   *
   * @param existing the collection exercise that is to be updated
   * @param candidatePeriod the proposed new value for the period (exerciseRef)
   * @param candidateSurvey the proposed new value for the survey
   * @throws CTPException thrown if there is an existing different collection exercise that already
   *     uses the proposed combination of period and survey
   */
  private void validateUniqueness(
      CollectionExercise existing, String candidatePeriod, UUID candidateSurvey)
      throws CTPException {
    if (!existing.getSurveyId().equals(candidateSurvey)
        || !existing.getExerciseRef().equals(candidatePeriod)) {
      CollectionExercise otherExisting = findCollectionExercise(candidatePeriod, candidateSurvey);

      if (otherExisting != null && !otherExisting.getId().equals(existing.getId())) {
        throw new CTPException(
            CTPException.Fault.RESOURCE_VERSION_CONFLICT,
            String.format(
                "A collection exercise with period %s and id %s already exists.",
                candidatePeriod, candidateSurvey));
      }
    }
  }

  /**
   * Update a collection exercise
   *
   * @param id the id of the collection exercise to update
   * @param collexDto the updated collection exercise
   * @return the updated CollectionExercise object
   */
  public CollectionExercise updateCollectionExercise(UUID id, CollectionExerciseDTO collexDto)
      throws CTPException {
    CollectionExercise existing = findCollectionExercise(id);

    if (existing == null) {
      throw new CTPException(
          CTPException.Fault.RESOURCE_NOT_FOUND,
          String.format("Collection exercise with id %s does not exist", id));
    } else {
      UUID surveyUuid = UUID.fromString(collexDto.getSurveyId());
      String period = collexDto.getExerciseRef();

      // This will throw exception if period & surveyId are not unique
      validateUniqueness(existing, period, surveyUuid);

      SurveyDTO survey = this.surveyService.findSurvey(surveyUuid);

      if (survey == null) {
        throw new CTPException(
            CTPException.Fault.BAD_REQUEST, String.format("Survey %s does not exist", surveyUuid));
      } else {
        setCollectionExerciseFromDto(collexDto, existing);
        existing.setUpdated(new Timestamp(new Date().getTime()));

        return updateCollectionExercise(existing);
      }
    }
  }

  /**
   * Update a collection exercise
   *
   * @param collex the updated collection exercise
   * @return the updated CollectionExercise object
   */
  public CollectionExercise updateCollectionExercise(final CollectionExercise collex) {
    collex.setUpdated(new Timestamp(new Date().getTime()));
    return this.collectRepo.saveAndFlush(collex);
  }

  /**
   * Utility method to set the deleted flag for a collection exercise
   *
   * @param id the uuid of the collection exercise to update
   * @param deleted true if the collection exercise is to be marked as deleted, false otherwise
   * @return 200 if success, 404 if not found
   * @throws CTPException thrown if specified collection exercise does not exist
   */
  private CollectionExercise updateCollectionExerciseDeleted(UUID id, boolean deleted)
      throws CTPException {
    CollectionExercise collex = findCollectionExercise(id);

    if (collex == null) {
      throw new CTPException(
          CTPException.Fault.RESOURCE_NOT_FOUND,
          String.format("Collection exercise %s does not exists", id));
    } else {
      collex.setDeleted(deleted);

      return updateCollectionExercise(collex);
    }
  }

  /**
   * Delete a collection exercise
   *
   * @param id the id of the collection exercise to delete
   * @return the updated CollectionExercise object
   * @throws CTPException thrown if error occurs
   */
  public CollectionExercise deleteCollectionExercise(UUID id) throws CTPException {
    return updateCollectionExerciseDeleted(id, true);
  }

  /**
   * Undelete a collection exercise
   *
   * @param id the id of the collection exercise to delete
   * @return the updated CollectionExercise object
   * @throws CTPException thrown if error occurs
   */
  public CollectionExercise undeleteCollectionExercise(UUID id) throws CTPException {
    return updateCollectionExerciseDeleted(id, false);
  }

  /**
   * Find all collection exercises with a given state
   *
   * @param state the state to find
   * @return a list of collection exercises with the given state
   */
  public List<CollectionExercise> findByState(CollectionExerciseDTO.CollectionExerciseState state) {
    return collectRepo.findByState(state);
  }

  /**
   * Utility method to transition a collection exercise to a new state
   *
   * @param collex a collection exercise
   * @param event a collection exercise event
   * @throws CTPException thrown if the specified event is not valid for the current state
   */
  public void transitionCollectionExercise(
      CollectionExercise collex, CollectionExerciseDTO.CollectionExerciseEvent event)
      throws CTPException {
    CollectionExerciseDTO.CollectionExerciseState oldState = collex.getState();
    CollectionExerciseDTO.CollectionExerciseState newState =
        collectionExerciseTransitionState.transition(collex.getState(), event);

    if (oldState == newState) {
      return;
    }

    collex.setState(newState);
    updateCollectionExercise(collex);
    rabbitTemplate.convertAndSend(new CollectionTransitionEvent(collex.getId(), collex.getState()));
  }

  /**
   * Utility method to transition a collection exercise to a new state
   *
   * @param collectionExerciseId a collection exercise UUID
   * @param event a collection exercise event
   * @throws CTPException thrown if the specified event is not valid for the current state or a
   *     collection exercise with the given id cannot be found
   */
  public void transitionCollectionExercise(
      final UUID collectionExerciseId, final CollectionExerciseDTO.CollectionExerciseEvent event)
      throws CTPException {
    CollectionExercise collex = findCollectionExercise(collectionExerciseId);

    if (collex == null) {
      throw new CTPException(
          CTPException.Fault.RESOURCE_NOT_FOUND,
          String.format("Cannot find collection exercise %s", collectionExerciseId));
    }

    transitionCollectionExercise(collex, event);
  }

  public void transitionScheduleCollectionExerciseToReadyToReview(
      final CollectionExercise collectionExercise) throws CTPException {
    UUID collexId = collectionExercise.getId();

    Map<String, String> searchStringMap =
        Collections.singletonMap("COLLECTION_EXERCISE", collectionExercise.getId().toString());
    String searchStringJson = new JSONObject(searchStringMap).toString();
    Integer numberOfCollectionInstruments =
        collectionInstrumentSvcClient.countCollectionInstruments(searchStringJson);
    boolean allSamplesActive = allSamplesActive(collexId);
    boolean shouldTransition =
        allSamplesActive
            && numberOfCollectionInstruments != null
            && numberOfCollectionInstruments > 0;
    log.with("all_samples_active", allSamplesActive)
        .with("number_of_collection_instruments", numberOfCollectionInstruments)
        .with("should_transition", shouldTransition)
        .info("ready for review transition check");
    if (shouldTransition) {
      transitionCollectionExercise(
          collectionExercise, CollectionExerciseDTO.CollectionExerciseEvent.CI_SAMPLE_ADDED);
    } else {
      transitionCollectionExercise(
          collectionExercise, CollectionExerciseDTO.CollectionExerciseEvent.CI_SAMPLE_DELETED);
    }
  }

  /**
   * Transition scheduled collection exercises with collection instruments and samples to {@link
   * CollectionExerciseDTO.CollectionExerciseState#READY_FOR_REVIEW}
   */
  public void transitionScheduleCollectionExerciseToReadyToReview(final UUID collectionExerciseId)
      throws CTPException {
    CollectionExercise collex = findCollectionExercise(collectionExerciseId);

    if (collex != null) {
      transitionScheduleCollectionExerciseToReadyToReview(collex);
    }
  }

  private boolean allSamplesActive(final UUID collexId) throws CTPException {
    List<SampleLink> sampleLinks = this.sampleLinkRepository.findByCollectionExerciseId(collexId);
    if (sampleLinks.isEmpty()) {
      return false;
    }

    return sampleLinks
        .stream()
        .map(sampleLink -> sampleSvcClient.getSampleSummary(sampleLink.getSampleSummaryId()))
        .allMatch(ss -> ss.getState().equals(SampleSummaryDTO.SampleState.ACTIVE));
  }

  /**
   * Links a sample summary to a collection exercise and stores in db
   *
   * @param sampleSummaryId the Id of the Sample summary to be linked
   * @param collectionExerciseId the Id of the Sample summary to be linked
   * @return sampleLink stored in database
   */
  SampleLink createLink(final UUID sampleSummaryId, final UUID collectionExerciseId) {
    SampleLink sampleLink = new SampleLink();
    sampleLink.setSampleSummaryId(sampleSummaryId);
    sampleLink.setCollectionExerciseId(collectionExerciseId);
    return sampleLinkRepository.saveAndFlush(sampleLink);
  }
}
