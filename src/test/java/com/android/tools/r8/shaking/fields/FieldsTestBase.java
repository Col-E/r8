// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.fields;

import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.invokesuper.Consumer;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;

public abstract class FieldsTestBase extends TestBase {

  public abstract Collection<Class<?>> getClasses();

  public abstract Class<?> getMainClass();

  public void testOnR8(List<String> keepRules, Consumer<CodeInspector> inspector, String expected)
      throws Throwable {
    testForR8(Backend.DEX)
        .enableMergeAnnotations()
        .addProgramClasses(getClasses())
        .addKeepRules(keepRules)
        .compile()
        .inspect(inspector)
        .run(getMainClass())
        .assertSuccessWithOutput(expected);
  }

  public void testOnProguard(
      List<String> keepRules, Consumer<CodeInspector> inspector, String expected) throws Throwable {
    testForProguard()
        .addProgramClasses(getClasses())
        .addProgramClasses(NeverMerge.class)
        .addKeepRules(keepRules)
        .compile()
        .inspect(inspector)
        .run(getMainClass())
        .assertSuccessWithOutput(expected);
  }

  public void runTest(List<String> keepRules, Consumer<CodeInspector> inspector, String expected)
      throws Throwable {
    testOnProguard(keepRules, inspector, expected);
    testOnR8(keepRules, inspector, expected);
  }

  public void runTest(String keepRules, Consumer<CodeInspector> inspector, String expected)
      throws Throwable {
    runTest(
        ImmutableList.of(
            keepRules,
            "-dontobfuscate",
            "-keep class " + getMainClass().getTypeName() + " {",
            "  public static void main(java.lang.String[]);",
            "}"),
        inspector,
        expected);
  }

  public String allFieldsOutput() {
    return StringUtils.lines("Super.f1 found", "Sub.f2 found", "SubSub.f3 found");
  }

  public String onlyF1Output() {
    return StringUtils.lines("Super.f1 found", "Sub.f2 not found", "SubSub.f3 not found");
  }

  public String onlyF2Output() {
    return StringUtils.lines("Super.f1 not found", "Sub.f2 found", "SubSub.f3 not found");
  }

  public String onlyF3Output() {
    return StringUtils.lines("Super.f1 not found", "Sub.f2 not found", "SubSub.f3 found");
  }
}
