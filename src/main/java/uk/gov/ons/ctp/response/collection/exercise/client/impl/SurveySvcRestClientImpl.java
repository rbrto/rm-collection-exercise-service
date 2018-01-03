package uk.gov.ons.ctp.response.collection.exercise.client.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.access.method.P;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import uk.gov.ons.ctp.common.rest.RestUtility;
import uk.gov.ons.ctp.response.collection.exercise.client.SurveySvcClient;
import uk.gov.ons.ctp.response.collection.exercise.config.AppConfig;
import uk.gov.ons.ctp.response.collection.exercise.domain.Survey;
import uk.gov.ons.response.survey.representation.SurveyClassifierDTO;
import uk.gov.ons.response.survey.representation.SurveyClassifierTypeDTO;
import uk.gov.ons.response.survey.representation.SurveyDTO;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * HTTP RestClient implementation for calls to the Survey service
 *
 */
@Component
@Slf4j
@Qualifier("restClient")
@Primary
public class SurveySvcRestClientImpl implements SurveySvcClient {

  @Autowired
  private AppConfig appConfig;

  @Autowired
  private RestTemplate restTemplate;

  @Qualifier("surveyRestUtility")
  @Autowired
  private RestUtility restUtility;

  @Autowired
  private ObjectMapper objectMapper;

  @Retryable(value = {RestClientException.class}, maxAttemptsExpression = "#{${retries.maxAttempts}}",
      backoff = @Backoff(delayExpression = "#{${retries.backoff}}"))
  @Override
  public List<SurveyClassifierDTO> requestClassifierTypeSelectors(final UUID surveyId) throws RestClientException {

    UriComponents uriComponents = restUtility.createUriComponents(
            appConfig.getSurveySvc().getRequestClassifierTypesListPath(), null, surveyId);

    HttpEntity<List<SurveyClassifierDTO>> httpEntity = restUtility.createHttpEntity(null);

    log.debug("about to get to the Survey SVC with surveyId {}", surveyId);
    ResponseEntity<String> responseEntity = restTemplate.exchange(uriComponents.toUri(), HttpMethod.GET, httpEntity,
        String.class);

    List<SurveyClassifierDTO> result = null;
    if (responseEntity != null && responseEntity.getStatusCode().is2xxSuccessful()) {
      String responseBody = responseEntity.getBody();
      try {
        result = objectMapper.readValue(responseBody, new TypeReference<List<SurveyClassifierDTO>>() { });
      } catch (IOException e) {
        String msg = String.format("cause = %s - message = %s", e.getCause(), e.getMessage());
        log.error(msg);
      }

    }

    return result;

  }

  @Override
  public SurveyClassifierTypeDTO requestClassifierTypeSelector(final UUID surveyId, final UUID classifierType)
      throws RestClientException {

    UriComponents uriComponents = restUtility.createUriComponents(
            appConfig.getSurveySvc().getRequestClassifierTypesPath(), null, surveyId, classifierType);

    HttpEntity<?> httpEntity = restUtility.createHttpEntity(null);

    log.debug("about to get to the Survey SVC with surveyId {} and classifierType {}", surveyId, classifierType);
    ResponseEntity<String> responseEntity = restTemplate.exchange(uriComponents.toUri(), HttpMethod.GET, httpEntity,
        String.class);

    SurveyClassifierTypeDTO result = null;
    if (responseEntity != null && responseEntity.getStatusCode().is2xxSuccessful()) {
      String responseBody = responseEntity.getBody();
      try {
        result = objectMapper.readValue(responseBody, SurveyClassifierTypeDTO.class);
      } catch (IOException e) {
        String msg = String.format("cause = %s - message = %s", e.getCause(), e.getMessage());
        log.error(msg);
      }
    }
    return result;
  }

  private SurveyDTO getSurveyDtoFromResponseEntity(ResponseEntity<String> responseEntity){
    SurveyDTO result = null;
    if (responseEntity != null && responseEntity.getStatusCode().is2xxSuccessful()) {
      String responseBody = responseEntity.getBody();
      try {
        result = objectMapper.readValue(responseBody, SurveyDTO.class);
      } catch (IOException e) {
        String msg = String.format("cause = %s - message = %s", e.getCause(), e.getMessage());
        log.error(msg);
      }
    }
    return result;
  }

  @Override
  public SurveyDTO findSurvey(UUID surveyId) {
    UriComponents uriComponents = restUtility.createUriComponents(
            appConfig.getSurveySvc().getSurveyDetailPath(), null, surveyId);

    HttpEntity<?> httpEntity = restUtility.createHttpEntity(null);

    try {
      log.debug("about to get to the Survey SVC with surveyId {}", surveyId);
      ResponseEntity<String> responseEntity = restTemplate.exchange(uriComponents.toUri(), HttpMethod.GET, httpEntity,
              String.class);

      return getSurveyDtoFromResponseEntity(responseEntity);
    } catch(RestClientException e){
      return null;
    }
  }

  @Override
  public SurveyDTO findSurveyByRef(String surveyRef) {
    UriComponents uriComponents = restUtility.createUriComponents(
            appConfig.getSurveySvc().getSurveyRefPath(), null, surveyRef);

    HttpEntity<?> httpEntity = restUtility.createHttpEntity(null);

    try {
      log.debug("about to get to the Survey SVC with surveyRef {}", surveyRef);
      ResponseEntity<String> responseEntity = restTemplate.exchange(uriComponents.toUri(), HttpMethod.GET, httpEntity,
              String.class);

      return getSurveyDtoFromResponseEntity(responseEntity);
    } catch(RestClientException e){
      return null;
    }
  }

}
