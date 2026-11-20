package dev.helpmycity.cityconfig

import dev.helpmycity.data.remote.external.ManualExternalRequestGateway

/**
 * Hand-off to the city's own request-a-service system, which for Oceanside is
 * "My Oceanside".
 *
 * My Oceanside is a white-label Wassabi Networks app with no public API, and the
 * city's follow-through on what lands there is...uneven. So this app stays the
 * system of record, and the city queue is somewhere we push to and check on by
 * hand. A manager files the request on the city's site, then records the
 * reference number here.
 *
 * If Oceanside ever ships an API, this class can implement
 * [dev.helpmycity.data.remote.external.ExternalRequestGateway] directly instead of
 * extending [ManualExternalRequestGateway]. Nothing above `data/remote` changes
 * either way.
 */
object CityRequestGateway : ManualExternalRequestGateway(
    // This gets saved on every ExternalReference, so it can never change.
    systemId = "my-oceanside",
    systemDisplayName = "My Oceanside",
    requestUrl = "https://www.ci.oceanside.ca.us/residents/city-services/" +
        "my-oceanside-request-a-service",
)
