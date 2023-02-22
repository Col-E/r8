// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApplyMappingMultipleInterfacesTest extends TestBase {

  public interface I1 {
    Object foo(String bar);
  }

  public interface I2 {
    String foo(String bar);
  }

  public interface I3 {
    String foo(String baz);
  }

  public interface I1Minified {
    Object a(String bar);
  }

  public interface I2Minified {
    String a(String bar);
  }

  public interface I3Minified {
    String a(String baz);
  }

  public static class ImplementsI2I3 implements I2, I3 {
    @Override
    public String foo(String bar) {
      System.out.print("Hello" + bar);
      return bar;
    }
  }

  public static class ImplementsI3 implements I3 {
    @Override
    public String foo(String bar) {
      System.out.print("Goodbye");
      return bar;
    }
  }

  public static class MainForImplements {

    public static void main(String[] args) {
      doSomething(args.length == 0 ? new ImplementsI2I3() : new ImplementsI3());
    }

    static void doSomething(I3 i3) {
      i3.foo(" World!");
    }
  }

  public static class MainWithLambda {

    public static void main(String[] args) {
      System.out.print(((I1 & I2) a -> a).foo("Hello World!"));
    }
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApplyMappingMultipleInterfacesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testApplyMappingCorrectNamingImplements()
      throws ExecutionException, CompilationFailedException, IOException {
    R8TestCompileResult libraryResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(I2Minified.class, I3Minified.class)
            .addKeepAllClassesRule()
            .setMinApi(parameters)
            .compile();
    testForR8(parameters.getBackend())
        .addClasspathClasses(I2.class, I3.class)
        .addProgramClasses(MainForImplements.class, ImplementsI2I3.class, ImplementsI3.class)
        .addKeepMainRule(MainForImplements.class)
        .addApplyMapping(
            I2.class.getTypeName()
                + " -> "
                + I2Minified.class.getTypeName()
                + ":\n"
                + "  java.lang.String foo(java.lang.String) -> a\n"
                + I3.class.getTypeName()
                + " -> "
                + I3Minified.class.getTypeName()
                + ":\n"
                + "  java.lang.String foo(java.lang.String) -> a")
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(libraryResult.writeToZip())
        .run(parameters.getRuntime(), MainForImplements.class)
        .assertSuccessWithOutput("Hello World!");
  }

  @Test
  public void testApplyMappingCorrectNamingLambda()
      throws ExecutionException, CompilationFailedException, IOException {
    R8TestCompileResult libraryResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(I1Minified.class, I2Minified.class)
            .addKeepAllClassesRule()
            .setMinApi(parameters)
            .compile();
    testForR8(parameters.getBackend())
        .addClasspathClasses(I1.class, I2.class)
        .addProgramClasses(MainWithLambda.class)
        .addKeepMainRule(MainWithLambda.class)
        .addApplyMapping(
            I1.class.getTypeName()
                + " -> "
                + I1Minified.class.getTypeName()
                + ":\n"
                + "  java.lang.Object foo(java.lang.String) -> a\n"
                + I2.class.getTypeName()
                + " -> "
                + I2Minified.class.getTypeName()
                + ":\n"
                + "  java.lang.String foo(java.lang.String) -> a")
        .setMinApi(parameters)
        .compile()
        .addRunClasspathFiles(libraryResult.writeToZip())
        .run(parameters.getRuntime(), MainWithLambda.class)
        .assertSuccessWithOutput("Hello World!");
  }
}
