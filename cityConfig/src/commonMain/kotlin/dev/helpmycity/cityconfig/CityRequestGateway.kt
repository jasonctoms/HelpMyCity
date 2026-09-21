package dev.helpmycity.cityconfig

import dev.helpmycity.data.remote.external.ManualExternalRequestGateway

/**
 * Hand-off to the city's own request-a-service system -- here, the City of
 * Oceanside's "My Oceanside".
 *
 * My Oceanside is a white-label Wassabi Networks app with no public API, and
 * the city's follow-through on what lands there is uneven, so this app stays
 * the system of record and the city queue is a destination we push to and poll
 * by hand: a manager files the request on the city's site, then records the
 * reference number here.
 *
 * Should Oceanside ship an API, this class implements
 * [dev.helpmycity.data.remote.external.ExternalRequestGateway]
 * directly instead of extending [ManualExternalRequestGateway]. Nothing above
 * `data/remote` changes either way.
 */
object CityRequestGateway : ManualExternalRequestGateway(
    // Persisted on every ExternalReference, so it must never change.
    systemId = "my-oceanside",
    systemDisplayName = "My Oceanside",
    requestUrl = "https://www.ci.oceanside.ca.us/residents/city-services/" +
        "my-oceanside-request-a-service",
)
