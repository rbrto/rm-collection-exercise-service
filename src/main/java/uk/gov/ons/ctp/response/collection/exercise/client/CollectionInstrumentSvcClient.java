package uk.gov.ons.ctp.response.collection.exercise.client;

import java.util.List;

import org.springframework.web.client.RestClientException;

import uk.gov.ons.ctp.response.collection.instrument.representation.CollectionInstrumentDTO;

/**
 * Service responsible for making client calls to the CollectionInstrument
 * service
 *
 */
public interface CollectionInstrumentSvcClient {

  /**
   * Request the existing collection instruments
   *
   * @param searchString search string for looking up collection instruments
   *          based on classifiers
   * @return list of collection instruments matching the search string
   * @throws RestClientException something went wrong making http call
   */
  List<CollectionInstrumentDTO> requestCollectionInstruments(String searchString) throws RestClientException;

}