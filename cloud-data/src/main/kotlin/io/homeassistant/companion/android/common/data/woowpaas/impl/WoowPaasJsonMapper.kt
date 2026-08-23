package io.homeassistant.companion.android.common.data.woowpaas.impl

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Kotlinx serialization [Json] instance used while interacting with the WOOW PaaS API.
 *
 * It is separate from the mapper used for Home Assistant Core so that the two contracts can evolve
 * independently. `ignoreUnknownKeys` keeps older applications working when the backend adds fields, and
 * the snake case naming strategy lets the DTOs stay idiomatic Kotlin without a `@SerialName` on every
 * property.
 */
@OptIn(ExperimentalSerializationApi::class)
internal val woowPaasJsonMapper = Json {
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}
