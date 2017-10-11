package uk.gov.ons.ctp.response.collection.exercise.endpoint;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;
import ma.glasnost.orika.MapperFacade;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.common.error.InvalidRequestException;
import uk.gov.ons.ctp.response.collection.exercise.domain.CaseType;
import uk.gov.ons.ctp.response.collection.exercise.domain.CollectionExercise;
import uk.gov.ons.ctp.response.collection.exercise.domain.Survey;
import uk.gov.ons.ctp.response.collection.exercise.representation.CaseTypeDTO;
import uk.gov.ons.ctp.response.collection.exercise.representation.CollectionExerciseDTO;
import uk.gov.ons.ctp.response.collection.exercise.representation.CollectionExerciseSummaryDTO;
import uk.gov.ons.ctp.response.collection.exercise.representation.LinkSampleSummaryDTO;
import uk.gov.ons.ctp.response.collection.exercise.representation.LinkSampleSummaryOutputDTO;
import uk.gov.ons.ctp.response.collection.exercise.service.CollectionExerciseService;
import uk.gov.ons.ctp.response.collection.exercise.service.SampleService;
import uk.gov.ons.ctp.response.collection.exercise.service.SurveyService;
import uk.gov.ons.ctp.response.sample.representation.SampleUnitsRequestDTO;

/**
 * The REST endpoint controller for Collection Exercises.
 */
@RestController
@RequestMapping(value = "/collectionexercises", produces = "application/json")
@Slf4j
public class CollectionExerciseEndpoint {

  private static final String RETURN_SAMPLENOTFOUND = "Sample not found for collection exercise Id";
  private static final String RETURN_COLLECTIONEXERCISENOTFOUND = "Collection Exercise not found for collection exercise Id";
  private static final String RETURN_SURVEYNOTFOUND = "Survey not found for survey Id";

  @Autowired
  private SampleService sampleService;

  @Autowired
  private CollectionExerciseService collectionExerciseService;

  @Autowired
  private SurveyService surveyService;

  @Qualifier("collectionExerciseBeanMapper")
  @Autowired
  private MapperFacade mapperFacade;

  /**
   * GET to find collection exercises from the collection exercise service for
   * the given survey Id.
   *
   * @param id survey Id for which to trigger delivery of collection exercises
   * @return list of collection exercises associated to survey
   * @throws CTPException on resource not found
   */
  @RequestMapping(value = "/survey/{id}", method = RequestMethod.GET)
  public ResponseEntity<List<CollectionExerciseSummaryDTO>> getCollectionExercisesForSurvey(
      @PathVariable("id") final UUID id) throws CTPException {

    Survey survey = surveyService.findSurvey(id);

    List<CollectionExerciseSummaryDTO> collectionExerciseSummaryDTOList;

    if (survey == null) {
      throw new CTPException(CTPException.Fault.RESOURCE_NOT_FOUND,
          String.format("%s %s", RETURN_SURVEYNOTFOUND, id));
    } else {
      log.debug("Entering collection exercise fetch with survey Id {}", id);
      List<CollectionExercise> collectionExerciseList = collectionExerciseService
          .findCollectionExercisesForSurvey(survey);
      collectionExerciseSummaryDTOList = mapperFacade.mapAsList(collectionExerciseList,
          CollectionExerciseSummaryDTO.class);
      if (collectionExerciseList.isEmpty()) {
        return ResponseEntity.noContent().build();
      }
    }

    return ResponseEntity.ok(collectionExerciseSummaryDTOList);
  }

  /**
   * GET to find collection exercise from the collection exercise service for
   * the given collection exercise Id.
   *
   * @param id collection exercise Id for which to trigger delivery of
   *          collection exercise
   * @return collection exercise associated to collection exercise id
   * @throws CTPException on resource not found
   */
  @RequestMapping(value = "/{id}", method = RequestMethod.GET)
  public ResponseEntity<CollectionExerciseDTO> getCollectionExercise(@PathVariable("id") final UUID id)
      throws CTPException {
    log.debug("Entering collection exercise fetch with collection exercise Id {}", id);
    CollectionExercise collectionExercise = collectionExerciseService.findCollectionExercise(id);
    if (collectionExercise == null) {
      throw new CTPException(CTPException.Fault.RESOURCE_NOT_FOUND,
          String.format("%s %s", RETURN_COLLECTIONEXERCISENOTFOUND, id));
    }

    Collection<CaseType> caseTypeList = collectionExerciseService.getCaseTypesList(collectionExercise);
    List<CaseTypeDTO> caseTypeDTOList = mapperFacade.mapAsList(caseTypeList, CaseTypeDTO.class);

    Survey survey = surveyService.findSurveyByFK(collectionExercise.getSurvey().getSurveyPK());

    CollectionExerciseDTO collectionExerciseDTO = mapperFacade.map(collectionExercise, CollectionExerciseDTO.class);
    collectionExerciseDTO.setCaseTypes(caseTypeDTOList);
    collectionExerciseDTO.setSurveyId(survey.getId().toString());

    return ResponseEntity.ok(collectionExerciseDTO);
  }

  /**
   * PUT to manually trigger the request of the sample units from the sample
   * service for the given collection exercise Id.
   *
   * @param id Collection exercise Id for which to trigger delivery of sample
   *          units
   * @return total sample units to be delivered.
   * @throws CTPException on resource not found
   */
  @RequestMapping(value = "/{id}", method = RequestMethod.PUT)
  public ResponseEntity<SampleUnitsRequestDTO> requestSampleUnits(@PathVariable("id") final UUID id)
      throws CTPException {
    log.debug("Entering collection exercise fetch with Id {}", id);
    SampleUnitsRequestDTO requestDTO = sampleService.requestSampleUnits(id);
    if (requestDTO == null) {
      throw new CTPException(CTPException.Fault.RESOURCE_NOT_FOUND,
          String.format("%s %s", RETURN_SAMPLENOTFOUND, id));
    }
    return ResponseEntity.ok(requestDTO);
  }

  @RequestMapping(value = "/link/{collectionExerciseId}", method = RequestMethod.PUT, consumes = "application/json")
  public ResponseEntity<List<LinkSampleSummaryOutputDTO>> linkSampleSummary(
      @PathVariable("collectionExerciseId") final UUID collectionExerciseId,
      @RequestBody(required = false) @Valid final LinkSampleSummaryDTO linkSampleSummaryDTO,
      BindingResult bindingResult) throws InvalidRequestException, CTPException {
    log.debug("Entering linkSampleSummary with collectionExerciseID {}", collectionExerciseId);

    if (bindingResult.hasErrors()) {
      throw new InvalidRequestException("Binding errors for execute action plan: ", bindingResult);
    }

    CollectionExercise collectionExercise = collectionExerciseService.findCollectionExercise(collectionExerciseId);
    if (collectionExercise == null) {
      throw new CTPException(CTPException.Fault.RESOURCE_NOT_FOUND,
          String.format("%s %s", RETURN_COLLECTIONEXERCISENOTFOUND, collectionExerciseId));
    }

    List<LinkSampleSummaryOutputDTO> result = collectionExerciseService.linkSampleSummaryToCollectionExercise(collectionExerciseId, linkSampleSummaryDTO.getSampleSummaryList());

    return ResponseEntity.ok(result);

  }
}
