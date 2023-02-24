// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.VerticallyMergedClassesInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergerVirtualToLibraryTest extends TestBase {

  private static final String[] EXPECTED = new String[] {"B::foo", "Lib::bar"};

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public VerticalClassMergerVirtualToLibraryTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(B.class, Main.class, A.class)
        .addLibraryClassFileData(classWithoutBarMethod(LibParent.class))
        .addLibraryClasses(Lib.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  private byte[] classWithoutBarMethod(Class<?> clazz) throws Exception {
    return transformer(clazz).removeMethodsWithName("bar").transform();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryClasses(LibParent.class)
        .addLibraryClassFileData(classWithoutBarMethod(Lib.class))
        .addProgramClasses(A.class, B.class, Main.class)
        .setMinApi(parameters)
        .compile()
        .addBootClasspathFiles(
            buildOnDexRuntime(
                parameters,
                classWithoutBarMethod(LibParent.class),
                transformer(Lib.class).transform()))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryClasses(LibParent.class)
        .addLibraryClassFileData(classWithoutBarMethod(Lib.class))
        .addProgramClasses(A.class, B.class, Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .addVerticallyMergedClassesInspector(
            VerticallyMergedClassesInspector::assertNoClassesMerged)
        .compile()
        .addBootClasspathFiles(
            buildOnDexRuntime(
                parameters,
                classWithoutBarMethod(LibParent.class),
                transformer(Lib.class).transform()))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(inspector -> assertThat(inspector.clazz(A.class), isPresent()));
  }

  public static class LibParent {

    /** Will be gone on older devices */
    public void bar() {
      System.out.println("LibParent::bar");
    }
  }

  public static class Lib extends LibParent {

    /** Will be present on older devices */
    @Override
    public void bar() {
      System.out.println("Lib::bar");
    }
  }

  public static class A extends Lib {

    @NeverInline
    public static void callSuper(A a) {
      a.bar();
    }
  }

  @NeverClassInline
  public static class B extends A {

    public void foo() {
      System.out.println("B::foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      B b = new B();
      b.foo();
      A.callSuper(b);
    }
  }
}
