package com.android.tools.r8.rewrite.assertions.kotlinassertionhandlerwithexceptions

fun methodWithAssertionError(assertion : Throwable) : String {
  return assertion.getStackTrace()[0].getMethodName();
}

fun assertionsWithCatch1() {
  try {
    assert(false) { "First assertion" }
  } catch (e : NoSuchMethodError) {
  } catch (e: NoSuchFieldError) {
  } catch (e : NoClassDefFoundError) {
  }
}

fun assertionsWithCatch2() {
  try {
    assert(false) { "Second assertion" }
  } catch (e : AssertionError) {
    println("Caught: " + e.message)
    try {
      assert(false) { "Third assertion" }
    } catch (e : AssertionError) {
      println("Caught: " + e.message)
    }
  }
}

fun simpleAssertion() {
  assert(false) { "Fifth assertion" }
}

fun assertionsWithCatch3() {
  try {
    assert(false) { "Fourth assertion" }
  } catch (e1 : AssertionError) {
    println("Caught from: " + methodWithAssertionError(e1));
    try {
      simpleAssertion();
    } catch (e2 : AssertionError) {
      println("Caught from: " + methodWithAssertionError(e2));
    }
  }
}

fun main() {
  try {
    assertionsWithCatch1();
  } catch (e : AssertionError) {
    // Ignore.
  }
  assertionsWithCatch2();
  assertionsWithCatch3();
}
