// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import java.util.List;
import org.hamcrest.Matcher;


public interface TestDiagnosticMessages {

  public List<Diagnostic> getInfos();

  public List<Diagnostic> getWarnings();

  public List<Diagnostic> getErrors();

  public TestDiagnosticMessages assertNoMessages();

  public TestDiagnosticMessages assertOnlyInfos();

  public TestDiagnosticMessages assertOnlyWarnings();

  public TestDiagnosticMessages assertInfosCount(int count);

  public TestDiagnosticMessages assertWarningsCount(int count);

  public TestDiagnosticMessages assertErrorsCount(int count);

  public TestDiagnosticMessages assertWarningMessageThatMatches(Matcher<String> matcher);

  public TestDiagnosticMessages assertNoWarningMessageThatMatches(Matcher<String> matcher);
}
