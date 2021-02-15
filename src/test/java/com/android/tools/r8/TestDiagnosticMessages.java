// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.not;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.hamcrest.Matcher;

public abstract class TestDiagnosticMessages {

  public abstract List<Diagnostic> getInfos();

  public abstract List<Diagnostic> getWarnings();

  @SuppressWarnings("unchecked")
  public final <D extends Diagnostic> D getWarning(int index) {
    return (D) getWarnings().get(index);
  }

  public abstract List<Diagnostic> getErrors();

  @SuppressWarnings("unchecked")
  public final <D extends Diagnostic> D getError(int index) {
    return (D) getErrors().get(index);
  }

  public abstract TestDiagnosticMessages assertNoMessages();

  public abstract TestDiagnosticMessages assertHasWarnings();

  public abstract TestDiagnosticMessages assertOnlyInfos();

  public abstract TestDiagnosticMessages assertOnlyWarnings();

  public abstract TestDiagnosticMessages assertOnlyErrors();

  public abstract TestDiagnosticMessages assertInfosCount(int count);

  public abstract TestDiagnosticMessages assertWarningsCount(int count);

  public abstract TestDiagnosticMessages assertErrorsCount(int count);

  public final TestDiagnosticMessages assertNoInfos() {
    return assertInfosCount(0);
  }

  public final TestDiagnosticMessages assertNoWarnings() {
    return assertWarningsCount(0);
  }

  public final TestDiagnosticMessages assertNoErrors() {
    return assertErrorsCount(0);
  }

  // Match exact.

  public final TestDiagnosticMessages assertDiagnosticsMatch(Matcher<Diagnostic> matcher) {
    return assertDiagnosticsMatch(Collections.singletonList(matcher));
  }

  public abstract TestDiagnosticMessages assertDiagnosticsMatch(
      Collection<Matcher<Diagnostic>> matchers);

  public final TestDiagnosticMessages assertInfosMatch(Matcher<Diagnostic> matcher) {
    return assertInfosMatch(Collections.singletonList(matcher));
  }

  public abstract TestDiagnosticMessages assertInfosMatch(Collection<Matcher<Diagnostic>> matchers);

  public final TestDiagnosticMessages assertWarningsMatch(Matcher<Diagnostic> matcher) {
    return assertWarningsMatch(Collections.singletonList(matcher));
  }

  @SafeVarargs
  public final TestDiagnosticMessages assertWarningsMatch(Matcher<Diagnostic>... matchers) {
    return assertWarningsMatch(Arrays.asList(matchers));
  }

  public abstract TestDiagnosticMessages assertWarningsMatch(
      Collection<Matcher<Diagnostic>> matchers);

  public final TestDiagnosticMessages assertErrorsMatch(Matcher<Diagnostic> matcher) {
    return assertErrorsMatch(Collections.singletonList(matcher));
  }

  public abstract TestDiagnosticMessages assertErrorsMatch(
      Collection<Matcher<Diagnostic>> matchers);

  // Match one.

  public abstract TestDiagnosticMessages assertDiagnosticThatMatches(Matcher<Diagnostic> matcher);

  public abstract TestDiagnosticMessages assertInfoThatMatches(Matcher<Diagnostic> matcher);

  public abstract TestDiagnosticMessages assertWarningThatMatches(Matcher<Diagnostic> matcher);

  public abstract TestDiagnosticMessages assertErrorThatMatches(Matcher<Diagnostic> matcher);

  // Consider removing this helper.
  public final TestDiagnosticMessages assertWarningMessageThatMatches(Matcher<String> matcher) {
    return assertWarningThatMatches(diagnosticMessage(matcher));
  }

  // Consider removing this helper.
  public final TestDiagnosticMessages assertErrorMessageThatMatches(Matcher<String> matcher) {
    return assertErrorThatMatches(diagnosticMessage(matcher));
  }

  // Match all.

  public abstract TestDiagnosticMessages assertAllDiagnosticsMatch(Matcher<Diagnostic> matcher);

  public abstract TestDiagnosticMessages assertAllInfosMatch(Matcher<Diagnostic> matcher);

  public abstract TestDiagnosticMessages assertAllWarningsMatch(Matcher<Diagnostic> matcher);

  public abstract TestDiagnosticMessages assertAllErrorsMatch(Matcher<Diagnostic> matcher);

  // Match none.

  public final TestDiagnosticMessages assertNoDiagnosticsMatch(Matcher<Diagnostic> matcher) {
    return assertAllDiagnosticsMatch(not(matcher));
  }

  public final TestDiagnosticMessages assertNoInfosMatch(Matcher<Diagnostic> matcher) {
    return assertAllInfosMatch(not(matcher));
  }

  public final TestDiagnosticMessages assertNoWarningsMatch(Matcher<Diagnostic> matcher) {
    return assertAllWarningsMatch(not(matcher));
  }

  public final TestDiagnosticMessages assertNoErrorsMatch(Matcher<Diagnostic> matcher) {
    return assertAllErrorsMatch(not(matcher));
  }
}
