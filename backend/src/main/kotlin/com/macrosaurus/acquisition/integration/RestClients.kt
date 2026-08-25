package com.macrosaurus.acquisition.integration

import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

internal fun restClient(
    baseUrl: String,
    connectTimeout: Duration,
    readTimeout: Duration,
): RestClient {
    val requestFactory =
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(connectTimeout)
            setReadTimeout(readTimeout)
        }
    return RestClient
        .builder()
        .baseUrl(baseUrl)
        .requestFactory(requestFactory)
        .build()
}
