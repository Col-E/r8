// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.b136698023;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProgramInterfaceNotImplementedTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  public interface I {

    Object foo();
  }

  public abstract static class A implements I {

    @Override
    public abstract Object foo();
  }

  public abstract static class B implements I {

    @Override
    public Object foo() {
      return "FOO";
    }
  }

  public static class C implements I {

    @Override
    public Object foo() {
      return "FOO";
    }
  }

  public abstract static class Lib {
    public abstract Object foo();
  }

  public static class D extends Lib implements I {

    @Override
    public Object foo() {
      return "FOO";
    }
  }

  public static class MainA {

    public static void main(String[] args) {
      System.out.println(A.class.toString());
    }
  }

  public static class MainB {

    public static void main(String[] args) {
      System.out.println(B.class.toString());
    }
  }

  public static class MainC {

    public static void main(String[] args) {
      System.out.println(C.class.toString());
    }
  }

  public static class MainD {

    public static void main(String[] args) {
      System.out.println(D.class.toString());
    }
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRemovingMethodInA()
      throws ExecutionException, CompilationFailedException, IOException {
    test(A.class, MainA.class);
  }

  @Test
  public void testRemovingMethodInB()
      throws ExecutionException, CompilationFailedException, IOException {
    test(B.class, MainB.class);
  }

  @Test
  public void testRemovingMethodInC()
      throws ExecutionException, CompilationFailedException, IOException {
    test(C.class, MainC.class);
  }

  private void test(Class<?> clazz, Class<?> main)
      throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class, clazz, main)
        .addKeepClassAndMembersRules(I.class)
        .addKeepMainRule(main)
        .addDontObfuscate()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), main)
        .assertSuccessWithOutputThatMatches(containsString(clazz.getTypeName()))
        .inspect(
            codeInspector -> {
              ClassSubject foundClazz = codeInspector.clazz(clazz);
              assertThat(foundClazz, isPresent());
              assertTrue(foundClazz.allMethods().isEmpty());
            });
  }

  @Test
  public void testRemovingMethodInD()
      throws ExecutionException, CompilationFailedException, IOException {
    R8TestCompileResult library =
        testForR8(parameters.getBackend())
            .addProgramClasses(Lib.class)
            .addKeepAllClassesRule()
            .setMinApi(parameters)
            .compile();
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class, D.class, MainD.class)
        .addLibraryClasses(Lib.class)
        .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
        .addKeepClassAndMembersRules(I.class)
        .addKeepMainRule(MainD.class)
        .setMinApi(parameters)
        .addDontObfuscate()
        .compile()
        .addRunClasspathFiles(library.writeToZip())
        .run(parameters.getRuntime(), MainD.class)
        .assertSuccessWithOutputThatMatches(containsString(D.class.getTypeName()));
  }
}
