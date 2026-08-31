package io.homeassistant.companion.android.common.data.woowpaas

import javax.inject.Qualifier

/**
 * Qualifier for the [io.homeassistant.companion.android.common.data.LocalStorage] holding the WOOW PaaS
 * session.
 *
 * It has its own storage file rather than sharing the Home Assistant session one: the two are unrelated
 * credentials with different lifetimes, and the PaaS one is dropped as soon as onboarding produced a
 * server.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class NamedWoowPaasStorage
