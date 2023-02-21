// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classlookup;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

// This test runs an R8 setup where a classpath class extends a program class and the program in
// turn uses the classpath class. Having such a cyclic compilation setup is very odd, but with
// sufficient keep rules it can work.
@RunWith(Parameterized.class)
public class ClasspathClassExtendsProgramClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClasspathClassExtendsProgramClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  public static class ProgramClass {

    public void foo() {
      System.out.println("ProgramClass::foo");
    }
  }

  static class Main {
    public static void main(String[] args) {
      ProgramClass object = args.length == 42 ? new ProgramClass() : new ClasspathClass();
      object.foo();
    }
  }

  public static class ClasspathIndirection extends ProgramClass {
    // Intentionally empty.
  }

  public static class ClasspathClass extends ClasspathIndirection {

    @Override
    public void foo() {
      System.out.println("ClasspathClass::foo");
    }
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, ProgramClass.class)
        .addRunClasspathFiles(
            compileToZip(
                parameters,
                ImmutableList.of(ProgramClass.class),
                ClasspathClass.class,
                ClasspathIndirection.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("ClasspathClass::foo");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(Main.class, ProgramClass.class)
        .addClasspathClasses(ClasspathClass.class, ClasspathIndirection.class)
        .addKeepMainRule(Main.class)
        // Keep the method that is overridden by the classpath class.
        .addKeepMethodRules(Reference.methodFromMethod(ProgramClass.class.getDeclaredMethod("foo")))
        .addRunClasspathFiles(
            compileToZip(
                parameters,
                ImmutableList.of(ProgramClass.class),
                ClasspathClass.class,
                ClasspathIndirection.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("ClasspathClass::foo");
  }
}
