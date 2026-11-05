/**
 * `-DfreshCompose`: build against the newer Compose/Skiko versions (the `*-fresh` entries in libs.versions.toml).
 *
 * A system property, not `-P`: the `plugins {}` block has no `Project`, so Gradle properties can't be read there.
 */
val freshCompose: Boolean = System.getProperty("freshCompose") != null
