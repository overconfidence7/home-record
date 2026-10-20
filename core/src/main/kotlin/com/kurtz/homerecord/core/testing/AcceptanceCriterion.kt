package com.kurtz.homerecord.core.testing

/** Marks a test as proof of one or more SPEC.md acceptance criteria, e.g. `@AcceptanceCriterion("AC-C8")`. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class AcceptanceCriterion(vararg val ids: String)
