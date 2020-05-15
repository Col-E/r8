// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.not;

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

  // Match exact.

  TestDiagnosticMessages assertDiagnosticsMatch(Matcher<Diagnostic>... matchers);

  TestDiagnosticMessages assertInfosMatch(Matcher<Diagnostic>... matchers);

  TestDiagnosticMessages assertWarningsMatch(Matcher<Diagnostic>... matchers);

  TestDiagnosticMessages assertErrorsMatch(Matcher<Diagnostic>... matchers);

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
