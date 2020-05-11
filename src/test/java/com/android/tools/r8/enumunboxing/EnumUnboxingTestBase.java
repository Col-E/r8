// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class EnumUnboxingTestBase extends TestBase {

  private static final String KEEP_ENUM_STUDIO =
      "-keepclassmembers enum * {\n"
          + " public static **[] values();\n"
          + " public static ** valueOf(java.lang.String);\n"
          + "}";
  private static final String KEEP_ENUM_SNAP =
      "-keepclassmembers enum * {\n"
          + "<fields>;\n"
          + " public static **[] values();\n"
          + " public static ** valueOf(java.lang.String);\n"
          + "}";
  private static final List<KeepRule> KEEP_ENUM =
      ImmutableList.of(
          new KeepRule("none", ""),
          new KeepRule("studio", KEEP_ENUM_STUDIO),
          new KeepRule("snap", KEEP_ENUM_SNAP));

  public static class KeepRule {
    private final String name;
    private final String keepRule;

    private KeepRule(String name, String keepRule) {
      this.name = name;
      this.keepRule = keepRule;
    }

    public String getKeepRule() {
      return keepRule;
    }

    @Override
    public String toString() {
      return name;
    }
  }

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
    options.enableEnumSwitchMapRemoval = enumValueOptimization;
    options.testing.enableEnumUnboxingDebugLogs = true;
  }

  void assertEnumIsUnboxed(Class<?> enumClass, String testName, TestDiagnosticMessages m) {
    assertTrue(enumClass.isEnum());
    Diagnostic diagnostic = m.getInfos().get(0);
    assertTrue(diagnostic.getDiagnosticMessage().startsWith("Unboxed enums"));
    assertTrue(
        StringUtils.joinLines(
            "Expected enum to be removed (" + testName + "):",
            m.getInfos().get(1).getDiagnosticMessage()),
        diagnostic.getDiagnosticMessage().contains(enumClass.getSimpleName()));
  }

  void assertEnumIsBoxed(Class<?> enumClass, String testName, TestDiagnosticMessages m) {
    assertTrue(enumClass.isEnum());
    Diagnostic diagnostic = m.getInfos().get(1);
    assertTrue(diagnostic.getDiagnosticMessage().startsWith("Boxed enums"));
    assertTrue(
        "Expected enum NOT to be removed (" + testName + ")",
        diagnostic.getDiagnosticMessage().contains(enumClass.getSimpleName()));
  }

  static List<Object[]> enumUnboxingTestParameters() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        BooleanUtils.values(),
        KEEP_ENUM);
  }

  static List<KeepRule> getAllEnumKeepRules() {
    return KEEP_ENUM;
  }
}
