package uk.gov.ons.ctp.response.collection.exercise.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import uk.gov.ons.ctp.response.collection.exercise.domain.CaseType;
import uk.gov.ons.ctp.response.collection.exercise.domain.CaseTypeDefault;
import uk.gov.ons.ctp.response.collection.exercise.domain.CaseTypeOverride;
import uk.gov.ons.ctp.response.collection.exercise.domain.CollectionExercise;
import uk.gov.ons.ctp.response.collection.exercise.domain.SampleLink;
import uk.gov.ons.ctp.response.collection.exercise.domain.Survey;
import uk.gov.ons.ctp.response.collection.exercise.repository.CaseTypeDefaultRepository;
import uk.gov.ons.ctp.response.collection.exercise.repository.CaseTypeOverrideRepository;
import uk.gov.ons.ctp.response.collection.exercise.repository.CollectionExerciseRepository;
import uk.gov.ons.ctp.response.collection.exercise.repository.SampleLinkRepository;
import uk.gov.ons.ctp.response.collection.exercise.repository.SurveyRepository;
import uk.gov.ons.ctp.response.collection.exercise.representation.LinkSampleSummaryOutputDTO;
import uk.gov.ons.ctp.response.collection.exercise.service.CollectionExerciseService;

/**
 * The implementation of the SampleService
 *
 */
@Service
@Slf4j
public class CollectionExerciseServiceImpl implements CollectionExerciseService {

  @Autowired
  private CollectionExerciseRepository collectRepo;

  @Autowired
  private CaseTypeOverrideRepository caseTypeOverrideRepo;

  @Autowired
  private CaseTypeDefaultRepository caseTypeDefaultRepo;

  @Autowired
  private SurveyRepository surveyRepo;

  @Autowired
  private SampleLinkRepository sampleLinkRepository;

  @Override
  public List<CollectionExercise> findCollectionExercisesForSurvey(Survey survey) {

    return collectRepo.findBySurveySurveyPK(survey.getSurveyPK());
  }

  @Override
  public CollectionExercise findCollectionExercise(UUID id) {

    return collectRepo.findOneById(id);
  }

  @Override
  public Collection<CaseType> getCaseTypesList(CollectionExercise collectionExercise) {

    Survey survey = surveyRepo.findById(collectionExercise.getSurvey().getId());

    List<CaseTypeDefault> caseTypeDefaultList = caseTypeDefaultRepo.findBySurveyFK(survey.getSurveyPK());

    List<CaseTypeOverride> caseTypeOverrideList = caseTypeOverrideRepo
        .findByExerciseFK(collectionExercise.getExercisePK());

    return createCaseTypeList(caseTypeDefaultList, caseTypeOverrideList);
  }

  /**
   * Creates a Collection of CaseTypes
   * 
   * @param caseTypeDefaultList List of caseTypeDefaults
   * @param caseTypeOverrideList List of caseTypeOverrides
   * @return Collection<CaseType> Collection of CaseTypes
   */
  public Collection<CaseType> createCaseTypeList(List<? extends CaseType> caseTypeDefaultList,
      List<? extends CaseType> caseTypeOverrideList) {

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
   * check that SampleSummary is not already linked to a CollectionExercise and
   * if not create link
   * 
   * @param collectionExerciseId the Id of the CollectionExercise to link to
   * @param sampleSummaryId the Id of the SampleSummary to be linked
   * @return LinkSampleSummaryOutputDTO the collection
   */
  @Transactional
  public List<LinkSampleSummaryOutputDTO> linkSampleSummaryToCollectionExercise(UUID collectionExerciseId, List<UUID> sampleSummaryIds) {

    sampleLinkRepository.deleteByCollectionExerciseId(collectionExerciseId);
    List<LinkSampleSummaryOutputDTO> linkedSummaries = new ArrayList<LinkSampleSummaryOutputDTO>();
    LinkSampleSummaryOutputDTO link= new LinkSampleSummaryOutputDTO();
    link.setCollectionExerciseId(collectionExerciseId);
    for (UUID summaryId : sampleSummaryIds) {
      createLink(summaryId, collectionExerciseId);
      link.setSampleSummaryId(summaryId);
      linkedSummaries.add(link);
    }
    return linkedSummaries;
  }

  /**
   * Links a sample summary to a collection exercise and stores in db
   * 
   * @param sampleSummaryId the Id of the Sample summary to be linked
   * @param collectionExerciseId the Id of the Sample summary to be linked
   */
  public void createLink(UUID sampleSummaryId, UUID collectionExerciseId) {
    SampleLink sampleLink = new SampleLink();
    sampleLink.setSampleSummaryId(sampleSummaryId);
    sampleLink.setCollectionExerciseId(collectionExerciseId);
    sampleLinkRepository.saveAndFlush(sampleLink);
  }

}
