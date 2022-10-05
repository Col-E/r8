// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageWithBridgeHoistingTest extends RepackageTestBase {

  public RepackageWithBridgeHoistingTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(
            TestClass.class, GreeterInterface.class, GreeterBase.class, Greeting.class)
        .addProgramClassFileData(
            transformer(PrintGreeter.class)
                .setBridge(PrintGreeter.class.getDeclaredMethod("greetBridge", Greeting.class))
                .transform(),
            transformer(PrintlnGreeter.class)
                .setBridge(PrintlnGreeter.class.getDeclaredMethod("greetBridge", Greeting.class))
                .transform())
        .addKeepMainRule(TestClass.class)
        .apply(this::configureRepackaging)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject greeterBase = inspector.clazz(GreeterBase.class);
    assertThat(greeterBase, isPresent());
    assertThat(greeterBase.uniqueMethodWithOriginalName("greetBridge"), isPresent());
  }

  public static class TestClass {

    public static void main(String[] args) {
      new PrintGreeter().greetBridge(new Greeting("Hello"));
      new PrintGreeter().greetBridge(new Greeting(" world!"));

      // Pass in an unknown greeter to prevent devirtualization in println().
      println(System.currentTimeMillis() >= 0 ? new PrintlnGreeter() : new PrintGreeter());
    }

    @NeverInline
    static void println(GreeterInterface greeter) {
      greeter.greet(new Greeting(""));
    }
  }

  @NoVerticalClassMerging
  public interface GreeterInterface {

    void greet(Greeting greeting);
  }

  public abstract static class GreeterBase implements GreeterInterface {

    // Will become the holder for greetBridge() after bridge hoisting. The bridge implementation
    // will be "invoke-virtual GreeterBase.greet()". This signature is a new non-rebound method
    // signature introduced by bridge hoisting.
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class PrintGreeter extends GreeterBase {

    // Will be hoisted to GreeterBase.greetBridge().
    @NeverInline
    public /*bridge*/ void greetBridge(Greeting greeting) {
      greet(greeting);
    }

    @NeverInline
    public void greet(Greeting greeting) {
      System.out.print(greeting);
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class PrintlnGreeter extends GreeterBase {

    // Will be hoisted to GreeterBase.greetBridge().
    @NeverInline
    public /*bridge*/ void greetBridge(Greeting greeting) {
      greet(greeting);
    }

    @NeverInline
    public void greet(Greeting greeting) {
      System.out.println(greeting);
    }
  }

  public static class Greeting {

    private final String greeting;

    public Greeting(String greeting) {
      this.greeting = greeting;
    }

    @Override
    public String toString() {
      return greeting;
    }
  }
}
