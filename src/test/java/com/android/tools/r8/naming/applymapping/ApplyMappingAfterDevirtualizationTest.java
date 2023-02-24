// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApplyMappingAfterDevirtualizationTest extends TestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("LibClassA::foo", "LibClassB::foo", "LibClassB::bar");

  public interface LibInterfaceA {
    void foo();
  }

  public interface LibInterfaceB {
    void foo();
  }

  // LibInterfaceA should be devirtualized into LibClassA
  public static class LibClassA implements LibInterfaceA {

    @Override
    @NoMethodStaticizing
    public void foo() {
      System.out.println("LibClassA::foo");
    }
  }

  // LibInterfaceB should be devirtualized into LibClassB
  public static class LibClassB implements LibInterfaceB {

    @Override
    @NoMethodStaticizing
    public void foo() {
      System.out.println("LibClassB::foo");
    }

    @NoMethodStaticizing
    public void bar() {
      System.out.println("LibClassB::bar");
    }

    public static void main(String[] args) {
      if (args.length > 0) {
        new LibClassB().foo();
      } else {
        new LibClassB().bar();
      }
    }
  }

  public static class ProgramClass {

    public static void main(String[] args) {
      new LibClassA().foo();
      LibClassB libClassB = new LibClassB();
      libClassB.foo();
      libClassB.bar();
    }
  }

  private static final Class<?>[] CLASSPATH_CLASSES = {
    LibInterfaceA.class, LibInterfaceB.class, LibClassA.class, LibClassB.class
  };

  private static final Class<?>[] PROGRAM_CLASSES = {ProgramClass.class};

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void runOnJvm() throws Throwable {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addClasspathClasses(CLASSPATH_CLASSES)
        .addProgramClasses(PROGRAM_CLASSES)
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void devirtualizingNoRenamingOfOverriddenNotKeptInterfaceMethods() throws Exception {
    R8TestCompileResult libraryResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(CLASSPATH_CLASSES)
            .addKeepClassAndMembersRulesWithAllowObfuscation(LibClassA.class)
            .addKeepMainRule(LibClassB.class)
            .addKeepClassAndDefaultConstructor(LibClassB.class)
            .addOptionsModification(options -> options.inlinerOptions().enableInlining = false)
            .enableNoMethodStaticizingAnnotations()
            .setMinApi(parameters)
            .compile();

    CodeInspector inspector = libraryResult.inspector();
    assertThat(inspector.clazz(LibClassA.class), isPresentAndRenamed());
    assertThat(inspector.clazz(LibClassB.class), isPresentAndNotRenamed());

    // LibInterfaceX should have been moved into LibClassX.
    assertThat(inspector.clazz(LibInterfaceA.class), not(isPresent()));
    assertThat(inspector.clazz(LibInterfaceB.class), not(isPresent()));

    testForR8(parameters.getBackend())
        .noTreeShaking()
        .addDontObfuscate()
        .addProgramClasses(PROGRAM_CLASSES)
        .addApplyMapping(libraryResult.getProguardMap())
        .addClasspathClasses(CLASSPATH_CLASSES)
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(libraryResult.writeToZip())
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void devirtualizingNoRenamingOfOverriddenKeptInterfaceMethods() throws Exception {
    R8TestCompileResult libraryResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(CLASSPATH_CLASSES)
            .addKeepClassAndMembersRulesWithAllowObfuscation(
                LibClassA.class, LibClassB.class, LibInterfaceA.class)
            .addOptionsModification(options -> options.inlinerOptions().enableInlining = false)
            .enableNoMethodStaticizingAnnotations()
            .setMinApi(parameters)
            .compile();

    CodeInspector inspector = libraryResult.inspector();
    assertThat(inspector.clazz(LibClassA.class), isPresentAndRenamed());
    assertThat(inspector.clazz(LibClassB.class), isPresentAndRenamed());

    // LibInterfaceA is now kept.
    assertThat(inspector.clazz(LibInterfaceA.class), isPresentAndRenamed());
    assertThat(inspector.clazz(LibInterfaceB.class), not(isPresent()));

    testForR8(parameters.getBackend())
        .noTreeShaking()
        .addDontObfuscate()
        .addProgramClasses(PROGRAM_CLASSES)
        .addApplyMapping(libraryResult.getProguardMap())
        .addClasspathClasses(CLASSPATH_CLASSES)
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(libraryResult.writeToZip())
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }
}
