// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.proguard.rules;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InnerClassNameSeparatorTest extends TestBase {

  private final TestParameters parameters;
  private final String separator;

  @Parameterized.Parameters(name = "{0}, separator: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), ImmutableList.of("$", "."));
  }

  public InnerClassNameSeparatorTest(TestParameters parameters, String separator) {
    this.parameters = parameters;
    this.separator = separator;
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addOptionsModification(InternalOptions::disableNameReflectionOptimization)
            .allowUnusedProguardConfigurationRules(separator.equals("."))
            .addProgramClassesAndInnerClasses(InnerClassNameSeparatorTestClass.class)
            .addKeepMainRule(InnerClassNameSeparatorTestClass.class)
            .addKeepRules(
                "-keep,allowobfuscation class "
                    + InnerClassNameSeparatorTestClass.class.getTypeName()
                    + separator
                    + InnerClassNameSeparatorTestClass.Inner.class.getSimpleName()
                    + " {",
                "  <init>(...);",
                "}")
            .setMinApi(parameters)
            .compile()
            .run(parameters.getRuntime(), InnerClassNameSeparatorTestClass.class);
    if (separator.equals("$")) {
      result.assertSuccessWithOutputLines("Hello world!");
    } else {
      result.assertFailureWithErrorThatMatches(containsString("NoSuchMethodException"));
    }
  }

  @Test
  public void testProguard() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    runTest(testForProguard()).assertSuccessWithOutputLines("Hello world!");
  }

  private TestRunResult<?> runTest(TestShrinkerBuilder<?, ?, ?, ?, ?> builder) throws Exception {
    return builder
        .addProgramClassesAndInnerClasses(InnerClassNameSeparatorTestClass.class)
        .addKeepMainRule(InnerClassNameSeparatorTestClass.class)
        .addKeepRules(
            "-keep,allowobfuscation class "
                + InnerClassNameSeparatorTestClass.class.getTypeName()
                + separator
                + InnerClassNameSeparatorTestClass.Inner.class.getSimpleName()
                + " {",
            "  <init>(...);",
            "}")
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), InnerClassNameSeparatorTestClass.class);
  }
}

class InnerClassNameSeparatorTestClass {

  public static void main(String[] args) throws Exception {
    String className = System.currentTimeMillis() >= 0 ? Inner.class.getName() : null;
    System.out.println(Class.forName(className).getConstructor().newInstance());
  }

  static class Inner {

    public Inner() {}

    @Override
    public String toString() {
      return "Hello world!";
    }
  }
}
