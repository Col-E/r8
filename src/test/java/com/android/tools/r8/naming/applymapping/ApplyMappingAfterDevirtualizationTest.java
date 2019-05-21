// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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
    public void foo() {
      System.out.println("LibClassA::foo");
    }
  }

  // LibClassB should be devirtualized into LibInterfaceB
  public static class LibClassB implements LibInterfaceB {

    @Override
    public void foo() {
      System.out.println("LibClassB::foo");
    }

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

  private static final Class<?>[] LIBRARY_CLASSES = {
    LibInterfaceA.class, LibInterfaceB.class, LibClassA.class, LibClassB.class
  };

  private static final Class<?>[] PROGRAM_CLASSES = {ProgramClass.class};

  private Backend backend;

  @Parameterized.Parameters(name = "{0}")
  public static Backend[] data() {
    return ToolHelper.getBackends();
  }

  public ApplyMappingAfterDevirtualizationTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void runOnJvm() throws Throwable {
    Assume.assumeTrue(backend == Backend.CF);
    testForJvm()
        .addProgramClasses(LIBRARY_CLASSES)
        .addProgramClasses(PROGRAM_CLASSES)
        .run(ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void devirtualizingNoRenamingOfOverriddenNotKeptInterfaceMethods() throws Exception {
    R8TestCompileResult libraryResult =
        testForR8(backend)
            .addProgramClasses(LIBRARY_CLASSES)
            .addKeepClassAndMembersRulesWithAllowObfuscation(LibClassA.class)
            .addKeepMainRule(LibClassB.class)
            .addOptionsModification(options -> options.enableInlining = false)
            .compile();

    CodeInspector inspector = libraryResult.inspector();
    assertThat(inspector.clazz(LibClassA.class), isPresent());
    assertThat(inspector.clazz(LibClassB.class), isPresent());

    // LibInterfaceX should have been moved into LibClassX.
    assertThat(inspector.clazz(LibInterfaceA.class), not(isPresent()));
    assertThat(inspector.clazz(LibInterfaceB.class), not(isPresent()));

    testForR8(backend)
        .noTreeShaking()
        .noMinification()
        .addProgramClasses(PROGRAM_CLASSES)
        .addApplyMapping(libraryResult.getProguardMap())
        .addLibraryClasses(LIBRARY_CLASSES)
        .addLibraryFiles(runtimeJar(backend))
        .compile()
        .addRunClasspathFiles(libraryResult.writeToZip())
        .run(ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void devirtualizingNoRenamingOfOverriddenKeptInterfaceMethods() throws Exception {
    R8TestCompileResult libraryResult =
        testForR8(backend)
            .addProgramClasses(LIBRARY_CLASSES)
            .addKeepClassAndMembersRulesWithAllowObfuscation(LibClassA.class, LibInterfaceA.class)
            .addKeepMainRule(LibClassB.class)
            .addOptionsModification(options -> options.enableInlining = false)
            .compile();

    CodeInspector inspector = libraryResult.inspector();
    assertThat(inspector.clazz(LibClassA.class), isPresent());
    assertThat(inspector.clazz(LibClassB.class), isPresent());

    // LibInterfaceA is now kept.
    assertThat(inspector.clazz(LibInterfaceA.class), isPresent());
    assertThat(inspector.clazz(LibInterfaceB.class), not(isPresent()));

    testForR8(backend)
        .noTreeShaking()
        .noMinification()
        .addProgramClasses(PROGRAM_CLASSES)
        .addApplyMapping(libraryResult.getProguardMap())
        .addLibraryClasses(LIBRARY_CLASSES)
        .addLibraryFiles(runtimeJar(backend))
        .compile()
        .addRunClasspathFiles(libraryResult.writeToZip())
        .run(ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }
}
