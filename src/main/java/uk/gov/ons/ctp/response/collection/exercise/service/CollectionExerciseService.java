package uk.gov.ons.ctp.response.collection.exercise.service;

import uk.gov.ons.ctp.response.collection.exercise.domain.CaseType;
import uk.gov.ons.ctp.response.collection.exercise.domain.CollectionExercise;
import uk.gov.ons.ctp.response.collection.exercise.domain.Survey;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for dealing with collection exercises
 *
 */
public interface CollectionExerciseService {


  /**
   * Find a list of surveys associated to a collection exercise Id from the Collection Exercise Service
   *
   * @param survey the survey for which to find collection exercises
   * @return the associated surveys.
   */
  List<CollectionExercise> findCollectionExercisesForSurvey(Survey survey);


  /**
   * Find a collection exercise associated to a collection exercise Id from the Collection Exercise Service
   *
   * @param id the collection exercise Id for which to find collection exercise
   * @return the associated collection exercise.
   */
  CollectionExercise findCollectionExercise(UUID id);

  /**
   * Find case types associated to a collection exercise from the Collection Exercise Service
   *
   * @param collectionExercise the collection exercise for which to find case types
   * @return the associated case type DTOs.
   */
  Collection<CaseType> getCaseTypesList(CollectionExercise collectionExercise);


}