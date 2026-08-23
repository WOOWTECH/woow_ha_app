package io.homeassistant.companion.android.common.data.woowpaas

/**
 * Lifecycle state of the Home Assistant instance attached to a WOOW PaaS account.
 *
 * Modelled as a sealed interface so callers exhaust every case at compile time instead of comparing
 * bare strings. [rawValue] keeps the wire representation around: it is what gets shown when a state
 * is reported in a place where it is not expected, and it is the only thing [Unknown] carries.
 *
 * The backend contract defines six values: `none`, `provisioning`, `ready`, `error`, `suspended` and
 * `deleting`. [Unknown] covers anything added later so an older application keeps working.
 */
sealed interface ProvisionStatus {

    /** The value received from the backend, kept verbatim for diagnostics and user facing messages. */
    val rawValue: String

    /** No instance has been requested for this account yet. */
    data object None : ProvisionStatus {
        override val rawValue: String = "none"
    }

    /** The instance is up and its URL is available. */
    data object Ready : ProvisionStatus {
        override val rawValue: String = "ready"
    }

    /** The instance is being created; the caller is expected to keep polling. */
    data object Provisioning : ProvisionStatus {
        override val rawValue: String = "provisioning"
    }

    /** Provisioning failed on the backend side. */
    data object Error : ProvisionStatus {
        override val rawValue: String = "error"
    }

    /** The instance exists but has been suspended, for instance because of a billing issue. */
    data object Suspended : ProvisionStatus {
        override val rawValue: String = "suspended"
    }

    /** The instance is being torn down. */
    data object Deleting : ProvisionStatus {
        override val rawValue: String = "deleting"
    }

    /**
     * A state this version of the application does not know about.
     *
     * Keeping unknown values as data instead of failing lets the backend introduce new states without
     * breaking older clients: the caller decides how to react, and nothing crashes.
     */
    data class Unknown(override val rawValue: String) : ProvisionStatus

    companion object {
        /**
         * Maps a raw backend status to its [ProvisionStatus], falling back to [Unknown] for anything
         * this version does not recognise.
         */
        fun from(rawValue: String): ProvisionStatus = when (rawValue) {
            None.rawValue -> None
            Provisioning.rawValue -> Provisioning
            Ready.rawValue -> Ready
            Error.rawValue -> Error
            Suspended.rawValue -> Suspended
            Deleting.rawValue -> Deleting
            else -> Unknown(rawValue)
        }
    }
}
