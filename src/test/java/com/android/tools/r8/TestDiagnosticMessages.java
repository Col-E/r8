// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

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

  TestDiagnosticMessages assertDiagnosticMessageThatMatches(Matcher<String> matcher);

  TestDiagnosticMessages assertInfoMessageThatMatches(Matcher<String> matcher);

  TestDiagnosticMessages assertAllInfoMessagesMatch(Matcher<String> matcher);

  TestDiagnosticMessages assertNoInfoMessageThatMatches(Matcher<String> matcher);

  TestDiagnosticMessages assertWarningMessageThatMatches(Matcher<String> matcher);

  TestDiagnosticMessages assertAllWarningMessagesMatch(Matcher<String> matcher);

  TestDiagnosticMessages assertNoWarningMessageThatMatches(Matcher<String> matcher);

  TestDiagnosticMessages assertErrorMessageThatMatches(Matcher<String> matcher);

  TestDiagnosticMessages assertNoErrorMessageThatMatches(Matcher<String> matcher);
}
