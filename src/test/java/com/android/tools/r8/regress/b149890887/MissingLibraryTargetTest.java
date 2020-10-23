// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b149890887;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MissingLibraryTargetTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("A::foo");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public MissingLibraryTargetTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static Class<?> MAIN = TestClass.class;
  private static List<Class<?>> PROGRAM = ImmutableList.of(MAIN, DerivedProgramClass.class);
  private static List<Class<?>> LIBRARY =
      ImmutableList.of(PresentLibraryInterface.class, PresentLibraryClass.class);

  private List<Path> runtimeClasspath() throws Exception {
    return buildOnDexRuntime(
        parameters,
        jarTestClasses(
            ImmutableList.<Class<?>>builder()
                .addAll(LIBRARY)
                .add(MissingLibraryClass.class)
                .build()));
  }

  private boolean isDesugaring() {
    return parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(AndroidApiLevel.N);
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(PROGRAM)
        .addRunClasspathFiles(runtimeClasspath())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(PROGRAM)
        .addKeepMainRule(MAIN)
        .addClasspathClasses(LIBRARY)
        .setMinApi(parameters.getApiLevel())
        .addKeepRules("-dontwarn")
        .compile()
        .addRunClasspathFiles(runtimeClasspath())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED);
  }

  // Non-present class with the actual definition.
  static class MissingLibraryClass {
    public void foo() {
      System.out.println("A::foo");
    }
  }

  // Present library interface declaring the method. This is needed to hit the member rebinding
  // issue. If not here the initial resolution will fail and no search will be done.
  interface PresentLibraryInterface {
    void foo();
  }

  // Present library class to trigger the search for the "first library definition".
  static class PresentLibraryClass extends MissingLibraryClass implements PresentLibraryInterface {
    // Intentionally empty.
  }

  // Program type that needs to be targeted to initiate rebinding.
  static class DerivedProgramClass extends PresentLibraryClass {
    // Intentionally empty.
  }

  static class TestClass {

    public static void main(String[] args) {
      new DerivedProgramClass().foo();
    }
  }
}
