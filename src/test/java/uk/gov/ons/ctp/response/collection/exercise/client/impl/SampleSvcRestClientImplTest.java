package uk.gov.ons.ctp.response.collection.exercise.client.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;

import java.net.URI;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import uk.gov.ons.ctp.common.error.CTPException;
import uk.gov.ons.ctp.common.rest.RestUtility;
import uk.gov.ons.ctp.common.rest.RestUtilityConfig;
import uk.gov.ons.ctp.response.sample.representation.SampleSummaryDTO;

@RunWith(MockitoJUnitRunner.class)
public class SampleSvcRestClientImplTest {

  @Spy private RestUtility restUtility = new RestUtility(RestUtilityConfig.builder().build());

  @Mock private RestTemplate restTemplate;

  @InjectMocks private SampleSvcRestClientImpl sampleSvcRestClient;

  @Test
  public void getSampleSummaries() throws CTPException {
    // Given
    UUID sampleSummaryId = UUID.randomUUID();
    SampleSummaryDTO sampleSummary = new SampleSummaryDTO();
    sampleSummary.setId(sampleSummaryId);
    sampleSummary.setState(SampleSummaryDTO.SampleState.ACTIVE);
    ResponseEntity<SampleSummaryDTO> responseEntity = Mockito.mock(ResponseEntity.class);
    given(responseEntity.getStatusCode()).willReturn(HttpStatus.OK);
    given(responseEntity.getBody()).willReturn(sampleSummary);
    given(
            restTemplate.exchange(
                any(URI.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(SampleSummaryDTO.class)))
        .willReturn(responseEntity);

    // When
    SampleSummaryDTO responseSampleSummary = sampleSvcRestClient.getSampleSummary(sampleSummaryId);

    // Then
    assertEquals(sampleSummaryId, responseSampleSummary.getId());
    assertEquals(SampleSummaryDTO.SampleState.ACTIVE, responseSampleSummary.getState());
  }

  @Test(expected = RestClientException.class)
  public void getSampleSummariesBadHttpCode() {
    // Given
    UUID sampleSummaryId = UUID.randomUUID();
    ResponseEntity<SampleSummaryDTO> responseEntity = Mockito.mock(ResponseEntity.class);
    given(
            restTemplate.exchange(
                any(URI.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(SampleSummaryDTO.class)))
        .willThrow(new RestClientException("Bad request"));

    // When
    sampleSvcRestClient.getSampleSummary(sampleSummaryId);

    // Then exception thrown
  }
}