// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.methods;

import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public abstract class MethodsTestBase extends TestBase {

  public enum Shrinker {
    Proguard,
    R8Full,
    R8Compat
  }

  public abstract Collection<Class<?>> getClasses();

  public abstract Class<?> getMainClass();

  public void testOnR8(
      List<String> keepRules, BiConsumer<CodeInspector, Shrinker> inspector, String expected)
      throws Throwable {
    testForR8(Backend.DEX)
        .enableMergeAnnotations()
        .addProgramClasses(getClasses())
        .addKeepRules(keepRules)
        .compile()
        .inspect(i -> inspector.accept(i, Shrinker.R8Full))
        .run(getMainClass())
        .assertSuccessWithOutput(expected);
  }

  public void testOnR8Compat(
      List<String> keepRules, BiConsumer<CodeInspector, Shrinker> inspector, String expected)
      throws Throwable {
    testForR8Compat(Backend.DEX)
        .enableMergeAnnotations()
        .addProgramClasses(getClasses())
        .addKeepRules(keepRules)
        .compile()
        .inspect(i -> inspector.accept(i, Shrinker.R8Compat))
        .run(getMainClass())
        .assertSuccessWithOutput(expected);
  }

  public void testOnProguard(
      List<String> keepRules, BiConsumer<CodeInspector, Shrinker> inspector, String expected)
      throws Throwable {
    testForProguard()
        .addProgramClasses(getClasses())
        .addProgramClasses(NeverMerge.class)
        .addKeepRules(keepRules)
        .compile()
        .inspect(i -> inspector.accept(i, Shrinker.Proguard))
        .run(getMainClass())
        .assertSuccessWithOutput(expected);
  }

  public void runTest(
      List<String> keepRules,
      BiConsumer<CodeInspector, Shrinker> inspector,
      Function<Shrinker, String> expected)
      throws Throwable {
    testOnProguard(keepRules, inspector, expected.apply(Shrinker.Proguard));
    testOnR8Compat(keepRules, inspector, expected.apply(Shrinker.R8Compat));
    testOnR8(keepRules, inspector, expected.apply(Shrinker.R8Full));
  }

  public void runTest(
      String keepRules, BiConsumer<CodeInspector, Shrinker> inspector, String expected)
      throws Throwable {
    runTest(keepRules, inspector, (unused) -> expected);
  }

  public void runTest(
      String keepRules,
      BiConsumer<CodeInspector, Shrinker> inspector,
      Function<Shrinker, String> expected)
      throws Throwable {
    runTest(
        ImmutableList.of(
            keepRules,
            keepMainProguardConfiguration(getMainClass()),
            "-dontobfuscate"),
        inspector,
        expected);
  }

  public String allMethodsOutput() {
    return StringUtils.lines("Super.m1 found", "Sub.m2 found", "SubSub.m3 found");
  }

  public String onlyM1Output() {
    return StringUtils.lines("Super.m1 found", "Sub.m2 not found", "SubSub.m3 not found");
  }

  public String onlyM2Output() {
    return StringUtils.lines("Super.m1 not found", "Sub.m2 found", "SubSub.m3 not found");
  }

  public String onlyM3Output() {
    return StringUtils.lines("Super.m1 not found", "Sub.m2 not found", "SubSub.m3 found");
  }
}
