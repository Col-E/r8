// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;

public class EnumUnboxingTestBase extends TestBase {

  static final String KEEP_ENUM =
      "-keepclassmembers enum * { public static **[] values(); public static **"
          + " valueOf(java.lang.String); }";

  public void assertLines2By2Correct(String string) {
    List<String> lines = StringUtils.splitLines(string);
    assert lines.size() % 2 == 0;
    for (int i = 0; i < lines.size(); i += 2) {
      assertEquals(
          "Different lines: " + lines.get(i) + " || " + lines.get(i + 1) + "\n" + string,
          lines.get(i),
          lines.get(i + 1));
    }
  }

  void enableEnumOptions(InternalOptions options, boolean enumValueOptimization) {
    options.enableEnumUnboxing = true;
    options.enableEnumValueOptimization = enumValueOptimization;
    options.testing.enableEnumUnboxingDebugLogs = true;
  }

  void assertEnumIsUnboxed(Class<?> enumClass, String testName, TestDiagnosticMessages m) {
    Diagnostic diagnostic = m.getInfos().get(0);
    assertTrue(diagnostic.getDiagnosticMessage().startsWith("Unboxed enums"));
    assertTrue(
        "Expected enum to be removed (" + testName + "): ",
        diagnostic.getDiagnosticMessage().contains(enumClass.getSimpleName()));
  }

  void assertEnumIsBoxed(Class<?> enumClass, String testName, TestDiagnosticMessages m) {
    Diagnostic diagnostic = m.getInfos().get(1);
    assertTrue(diagnostic.getDiagnosticMessage().startsWith("Boxed enums"));
    assertTrue(
        "Expected enum NOT to be removed (" + testName + "): ",
        diagnostic.getDiagnosticMessage().contains(enumClass.getSimpleName()));
  }

  static List<Object[]> enumUnboxingTestParameters() {
    return buildParameters(
        getTestParameters()
            .withCfRuntime(CfVm.JDK9)
            .withDexRuntime(DexVm.Version.first())
            .withDexRuntime(DexVm.Version.last())
            .withAllApiLevels()
            .build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }
}
