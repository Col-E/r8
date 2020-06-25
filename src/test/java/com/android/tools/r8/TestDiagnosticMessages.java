// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.not;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.hamcrest.Matcher;

public interface TestDiagnosticMessages {

  List<Diagnostic> getInfos();

  List<Diagnostic> getWarnings();

  List<Diagnostic> getErrors();

  TestDiagnosticMessages assertNoMessages();

  TestDiagnosticMessages assertOnlyInfos();

  TestDiagnosticMessages assertOnlyWarnings();

  TestDiagnosticMessages assertOnlyErrors();

  TestDiagnosticMessages assertInfosCount(int count);

  TestDiagnosticMessages assertWarningsCount(int count);

  TestDiagnosticMessages assertErrorsCount(int count);

  default TestDiagnosticMessages assertNoInfos() {
    return assertInfosCount(0);
  }

  default TestDiagnosticMessages assertNoWarnings() {
    return assertWarningsCount(0);
  }

  default TestDiagnosticMessages assertNoErrors() {
    return assertErrorsCount(0);
  }

  // Match exact.

  default TestDiagnosticMessages assertDiagnosticsMatch(Matcher<Diagnostic> matcher) {
    return assertDiagnosticsMatch(Collections.singletonList(matcher));
  }

  TestDiagnosticMessages assertDiagnosticsMatch(Collection<Matcher<Diagnostic>> matchers);

  default TestDiagnosticMessages assertInfosMatch(Matcher<Diagnostic> matcher) {
    return assertInfosMatch(Collections.singletonList(matcher));
  }

  TestDiagnosticMessages assertInfosMatch(Collection<Matcher<Diagnostic>> matchers);

  default TestDiagnosticMessages assertWarningsMatch(Matcher<Diagnostic> matcher) {
    return assertWarningsMatch(Collections.singletonList(matcher));
  }

  TestDiagnosticMessages assertWarningsMatch(Collection<Matcher<Diagnostic>> matchers);

  default TestDiagnosticMessages assertErrorsMatch(Matcher<Diagnostic> matcher) {
    return assertErrorsMatch(Collections.singletonList(matcher));
  }

  TestDiagnosticMessages assertErrorsMatch(Collection<Matcher<Diagnostic>> matchers);

  // Match one.

  TestDiagnosticMessages assertDiagnosticThatMatches(Matcher<Diagnostic> matcher);

  TestDiagnosticMessages assertInfoThatMatches(Matcher<Diagnostic> matcher);

  TestDiagnosticMessages assertWarningThatMatches(Matcher<Diagnostic> matcher);

  TestDiagnosticMessages assertErrorThatMatches(Matcher<Diagnostic> matcher);

  // Consider removing this helper.
  default TestDiagnosticMessages assertWarningMessageThatMatches(Matcher<String> matcher) {
    return assertWarningThatMatches(diagnosticMessage(matcher));
  }

  // Consider removing this helper.
  default TestDiagnosticMessages assertErrorMessageThatMatches(Matcher<String> matcher) {
    return assertErrorThatMatches(diagnosticMessage(matcher));
  }

  // Match all.

  TestDiagnosticMessages assertAllDiagnosticsMatch(Matcher<Diagnostic> matcher);

  TestDiagnosticMessages assertAllInfosMatch(Matcher<Diagnostic> matcher);

  TestDiagnosticMessages assertAllWarningsMatch(Matcher<Diagnostic> matcher);

  TestDiagnosticMessages assertAllErrorsMatch(Matcher<Diagnostic> matcher);

  // Match none.

  default TestDiagnosticMessages assertNoDiagnosticsMatch(Matcher<Diagnostic> matcher) {
    return assertAllDiagnosticsMatch(not(matcher));
  }

  default TestDiagnosticMessages assertNoInfosMatch(Matcher<Diagnostic> matcher) {
    return assertAllInfosMatch(not(matcher));
  }

  default TestDiagnosticMessages assertNoWarningsMatch(Matcher<Diagnostic> matcher) {
    return assertAllWarningsMatch(not(matcher));
  }

  default TestDiagnosticMessages assertNoErrorsMatch(Matcher<Diagnostic> matcher) {
    return assertAllErrorsMatch(not(matcher));
  }
}
