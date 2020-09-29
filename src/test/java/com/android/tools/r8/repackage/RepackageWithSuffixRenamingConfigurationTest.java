// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.repackaging.Repackaging.SuffixRenamingRepackagingConfiguration;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageWithSuffixRenamingConfigurationTest extends RepackageTestBase {

  public RepackageWithSuffixRenamingConfigurationTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepClassRules(GreeterFoo.class)
        .addOptionsModification(
            options ->
                options.testing.repackagingConfigurationFactory =
                    appView ->
                        new SuffixRenamingRepackagingConfiguration("Foo", appView.dexItemFactory()))
        .apply(this::configureRepackaging)
        .enableInliningAnnotations()
        .noMinification()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject greeterSubject = inspector.clazz(Greeter.class);
    assertEquals(GreeterFoo.class.getTypeName() + "$1", greeterSubject.getFinalName());

    ClassSubject greeterFooSubject = inspector.clazz(GreeterFoo.class);
    assertEquals(GreeterFoo.class.getTypeName(), greeterFooSubject.getFinalName());
  }

  public static class TestClass {

    public static void main(String[] args) {
      Greeter.greet();
      GreeterFoo.greet();
    }
  }

  public static class Greeter extends Exception {

    @NeverInline
    public static void greet() {
      System.out.print("Hello");
    }
  }

  public static class GreeterFoo extends Exception {

    @NeverInline
    public static void greet() {
      System.out.println(" world!");
    }
  }
}
