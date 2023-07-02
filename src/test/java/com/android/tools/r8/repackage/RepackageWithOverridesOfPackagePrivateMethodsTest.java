// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoParameterTypeStrengthening;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageWithOverridesOfPackagePrivateMethodsTest extends RepackageTestBase {

  public RepackageWithOverridesOfPackagePrivateMethodsTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .apply(this::configureRepackaging)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoAccessModificationAnnotationsForMembers()
        .enableNoParameterTypeStrengtheningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    assertThat(HelloGreeter.class, isNotRepackaged(inspector));
    assertThat(WorldGreeter.class, isNotRepackaged(inspector));
  }

  public static class TestClass {

    public static void main(String[] args) {
      greet(new HelloGreeter());
      greet(new WorldGreeter());
    }

    @NeverInline
    @NoParameterTypeStrengthening
    static void greet(HelloGreeterBase greeter) {
      greeter.greet();
    }

    @NeverInline
    @NoParameterTypeStrengthening
    static void greet(WorldGreeterBase greeter) {
      greeter.greet();
    }
  }

  @NoVerticalClassMerging
  public abstract static class HelloGreeterBase {

    @NoAccessModification
    abstract void greet();
  }

  @NeverClassInline
  public static class HelloGreeter extends HelloGreeterBase {

    @NeverInline
    @Override
    void greet() {
      System.out.print("Hello");
    }
  }

  @NoVerticalClassMerging
  public abstract static class WorldGreeterBase {

    @NoAccessModification
    abstract void greet();
  }

  @NeverClassInline
  public static class WorldGreeter extends WorldGreeterBase {

    @NeverInline
    @Override
    public void greet() {
      System.out.println(" world!");
    }
  }
}
