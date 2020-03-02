// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping.desugar;

import static com.android.tools.r8.references.Reference.classFromClass;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.desugar.InterfaceMethodRewriter;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DefaultInterfaceMethodTest extends TestBase {

  public static final String OUTPUT = "Called LibraryInterface::foo";
  public static final String EXPECTED = StringUtils.lines(OUTPUT);

  public interface LibraryInterface {
    default void foo() {
      System.out.println(OUTPUT);
    }
  }

  public static class ProgramClass implements LibraryInterface {

    public static void main(String[] args) {
      new ProgramClass().foo();
    }
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public DefaultInterfaceMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Throwable {
    Assume.assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramClasses(LibraryInterface.class, ProgramClass.class)
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testFullProgram() throws Throwable {
    testForR8(parameters.getBackend())
        .addProgramClasses(LibraryInterface.class, ProgramClass.class)
        .addKeepMainRule(ProgramClass.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testLibraryLinkedWithProgram() throws Throwable {
    String ruleContent = "-keep class " + LibraryInterface.class.getTypeName() + " { *; }";
    R8TestCompileResult libraryResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(LibraryInterface.class)
            .addKeepRules(ruleContent)
            .setMinApi(parameters.getApiLevel())
            .compile();
    CodeInspector inspector = libraryResult.inspector();
    assertTrue(inspector.clazz(LibraryInterface.class).isPresent());
    assertTrue(inspector.method(LibraryInterface.class.getMethod("foo")).isPresent());
    if (willDesugarDefaultInterfaceMethods(parameters.getApiLevel())) {
      ClassSubject companion = inspector.clazz(Reference.classFromDescriptor(
          InterfaceMethodRewriter.getCompanionClassDescriptor(
              classFromClass(LibraryInterface.class).getDescriptor())));
      // Check that we included the companion class.
      assertTrue(companion.isPresent());
      // TODO(b/129223905): Check the method is also present on the companion class.
      assertTrue(inspector.method(LibraryInterface.class.getMethod("foo")).isPresent());
    }

    testForR8(parameters.getBackend())
        .noTreeShaking()
        .addProgramClasses(ProgramClass.class)
        .addClasspathClasses(LibraryInterface.class)
        .addApplyMapping(libraryResult.getProguardMap())
        .addKeepMainRule(ProgramClass.class)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .addRunClasspathFiles(libraryResult.writeToZip())
        .run(parameters.getRuntime(), ProgramClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private static boolean willDesugarDefaultInterfaceMethods(AndroidApiLevel apiLevel) {
    return apiLevel != null && apiLevel.getLevel() < AndroidApiLevel.N.getLevel();
  }
}
