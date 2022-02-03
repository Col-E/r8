package com.android.tools.r8.rewrite.assertions.kotlinassertionhandlersimple

fun simpleAssertion() {
  assert(false) { "simpleAssertion" }
}

fun multipleAssertions() {
  assert(false) { "multipleAssertions 1" }
  assert(false) { "multipleAssertions 2" }
}

fun main() {
  simpleAssertion();
  multipleAssertions();
}