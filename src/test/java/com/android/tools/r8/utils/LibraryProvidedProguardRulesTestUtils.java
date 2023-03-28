// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.Version;
import org.hamcrest.Matcher;

public class LibraryProvidedProguardRulesTestUtils {

  public static Matcher<Diagnostic> getDiagnosticMatcher() {
    return allOf(
        diagnosticType(StringDiagnostic.class), diagnosticMessage(getDiagnosticMessageMatcher()));
  }

  public static Matcher<String> getDiagnosticMessageMatcher() {
    return equalTo(
        "Running R8 version "
            + Version.getVersionString()
            + ", which cannot be represented as a semantic version. Using an artificial version "
            + "newer than any known version for selecting Proguard configurations embedded under "
            + "META-INF/. This means that all rules with a '-upto-' qualifier will be excluded and "
            + "all rules with a -from- qualifier will be included.");
  }
}
