package com.rkhamatyarov.laret.config.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Defaults for `watch live`, overridable by CLI flags (12-factor order).
 *
 * @property debounceMs coalesce window in milliseconds.
 * @property patterns default include/exclude globs (an `!` prefix excludes).
 * @property maxRestarts total re-run cap (0 = unlimited).
 * @property maxConsecutiveFailures back-to-back failure cap (0 = off).
 */
data class WatchConfig(
    @field:JsonProperty("debounce-ms") val debounceMs: Long = 150,
    @field:JsonProperty("patterns") val patterns: List<String> = emptyList(),
    @field:JsonProperty("max-restarts") val maxRestarts: Int = 0,
    @field:JsonProperty("max-consecutive-failures") val maxConsecutiveFailures: Int = 0,
)
