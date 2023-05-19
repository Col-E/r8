// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.enclosingmethod;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.examples.ExamplesTestBase;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EnclosingMethodTestRunner extends ExamplesTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().enableApiLevelsForCf().build();
  }

  public EnclosingMethodTestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return Main.class;
  }

  @Override
  public List<Class<?>> getTestClasses() throws Exception {
    return ImmutableList.of(
        getMainClass(),
        AbstractClass.class,
        OuterClass.class,
        OuterClass.AClass.class,
        Class.forName(OuterClass.class.getTypeName() + "$1"),
        Class.forName(OuterClass.class.getTypeName() + "$2"),
        Class.forName(OuterClass.class.getTypeName() + "$1AnotherClass"),
        Class.forName(OuterClass.class.getTypeName() + "$1LocalClass"));
  }

  private boolean isDalvikWithIncorrectBehavior() {
    // Dalvik does not correctly report the enclosing classes.
    return parameters.isDexRuntimeVersionOlderThanOrEqual(Version.V4_4_4);
  }

  @Override
  public String getExpected() {
    return StringUtils.lines(
        "42",
        "class com.android.tools.r8.examples.enclosingmethod.OuterClass",
        "null",
        "true",
        isDalvikWithIncorrectBehavior() ? "true" : "false",
        "7",
        "class com.android.tools.r8.examples.enclosingmethod.OuterClass",
        "null",
        "false",
        "true",
        "42",
        "class com.android.tools.r8.examples.enclosingmethod.OuterClass",
        "public void com.android.tools.r8.examples.enclosingmethod.OuterClass.aMethod()",
        "true",
        "false",
        "48",
        "class com.android.tools.r8.examples.enclosingmethod.OuterClass",
        "public void com.android.tools.r8.examples.enclosingmethod.OuterClass.aMethod()",
        "false",
        "true",
        "InnerClass com.android.tools.r8.examples.enclosingmethod.OuterClass$AClass");
  }

  @Test
  public void testDesugaring() throws Exception {
    Assume.assumeFalse(isDalvikWithIncorrectBehavior());
    runTestDesugaring();
  }

  @Test
  public void testR8() throws Exception {
    // The program reflects on inner-outer classes so disable all shrinking and optimization.
    runTestR8(b -> b.addDontShrink().addDontOptimize().addDontObfuscate().addKeepAllAttributes());
  }

  @Ignore("TODO(b/281805219): R8 output steps to a different line number.")
  @Test
  public void testDebug() throws Exception {
    runTestDebugComparator();
  }
}
