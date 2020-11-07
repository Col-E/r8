// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.references.Reference.methodFromMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryOverrideClassInliningTest extends TestBase {

  @NoHorizontalClassMerging
  public static class SimpleLibraryOverride implements Runnable {

    @NeverInline
    @Override
    public void run() {
      Main.println("running...");
    }
  }

  public static class NonSimpleLibraryOverride implements Runnable {

    @NeverInline
    @Override
    public void run() {
      char[] buffer = new char[System.nanoTime() < 0 ? (int) System.nanoTime() : 100];
      String greeting = "Hello world!";
      for (int i = 0; i < greeting.length(); i++) {
        buffer[i] = greeting.charAt(i);
      }
      StringBuilder result = new StringBuilder();
      int j = 0;
      for (char c : greeting.toCharArray()) {
        result.append(buffer[j++]);
      }
      Main.println(result.toString());
    }
  }

  public static class Main {

    // Print method to ensure an instruction count within the max 3.
    @NeverInline
    public static void println(String string) {
      System.out.println(string);
    }

    public static void main(String[] args) {
      // Potential class inlining candidate.
      new SimpleLibraryOverride().run();
      new NonSimpleLibraryOverride().run();
      // Escaping usage of the class ensuring the library method must be kept.
      if (System.nanoTime() < 0) {
        System.out.println(new SimpleLibraryOverride());
        System.out.println(new NonSimpleLibraryOverride());
      }
    }
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public final TestParameters parameters;

  public LibraryOverrideClassInliningTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .enableInliningAnnotations()
            .noMinification()
            .addProgramClasses(
                Main.class, SimpleLibraryOverride.class, NonSimpleLibraryOverride.class)
            .addKeepMainRule(Main.class)
            .enableNoHorizontalClassMergingAnnotations()
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), Main.class)
            .assertSuccessWithOutputLines("running...", "Hello world!")
            .inspector();
    assertThat(inspector.clazz(SimpleLibraryOverride.class), isPresent());
    assertThat(inspector.clazz(NonSimpleLibraryOverride.class), isPresent());
    MethodSubject main =
        inspector.method(methodFromMethod(Main.class.getMethod("main", String[].class)));
    // Check the simple run() is inlined.
    assertTrue(
        "Unexpected Simple.run invoke in:\n" + main.getMethod().codeToString(),
        main.streamInstructions()
            .noneMatch(
                i ->
                    i.isInvoke()
                        && i.getMethod().name.toString().equals("run")
                        && i.getMethod()
                            .holder
                            .toString()
                            .equals(SimpleLibraryOverride.class.getTypeName())));
    // Check the non-simple run is not inlined.
    assertTrue(
        "Expected NonSimple.run invoke in:\n" + main.getMethod().codeToString(),
        main.streamInstructions()
            .anyMatch(
                i ->
                    i.isInvoke()
                        && i.getMethod().name.toString().equals("run")
                        && i.getMethod()
                            .holder
                            .toString()
                            .equals(NonSimpleLibraryOverride.class.getTypeName())));
  }
}
