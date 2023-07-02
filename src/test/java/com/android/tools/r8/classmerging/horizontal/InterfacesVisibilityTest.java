// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isImplementing;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.classmerging.horizontal.testclasses.InterfacesVisibilityTestClasses;
import com.android.tools.r8.classmerging.horizontal.testclasses.InterfacesVisibilityTestClasses.ImplementingPackagePrivateInterface;
import com.android.tools.r8.classmerging.horizontal.testclasses.InterfacesVisibilityTestClasses.Invoker;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import org.junit.Test;

public class InterfacesVisibilityTest extends HorizontalClassMergingTestBase {
  public InterfacesVisibilityTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    String packagePrivateInterfaceName =
        InterfacesVisibilityTestClasses.class.getTypeName() + "$PackagePrivateInterface";
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass(), InterfacesVisibilityTestClasses.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoAccessModificationAnnotationsForClasses()
        .enableNoMethodStaticizingAnnotations()
        .enableNoParameterTypeStrengtheningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .compile()
        .inspect(
            codeInspector -> {
              // A is present and has no interfaces.
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertTrue(
                  codeInspector.clazz(A.class).getDexProgramClass().getInterfaces().isEmpty());

              // ImplementingPackagePrivateInterface is present and implements
              // PackagePrivateInterface.
              assertThat(
                  codeInspector.clazz(ImplementingPackagePrivateInterface.class),
                  isImplementing(codeInspector.clazz(packagePrivateInterfaceName)));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foo", "bar");
  }

  @NeverClassInline
  public static class A {
    @NeverInline
    @NoMethodStaticizing
    public void bar() {
      System.out.println("bar");
    }
  }

  public static class Main {
    @NeverInline
    public static void foo(ImplementingPackagePrivateInterface o) {
      Invoker.invokeFoo(o);
    }

    public static void main(String[] args) {
      ImplementingPackagePrivateInterface implementingPackagePrivateInterface =
          new ImplementingPackagePrivateInterface();
      A a = new A();
      Invoker.invokeFoo(implementingPackagePrivateInterface);
      a.bar();
    }
  }
}
