// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import static com.android.tools.r8.classmerging.vertical.testclasses.NoIllegalClassAccessWithAccessModificationsTestClasses.getSimpleInterfaceImplClass;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.classmerging.vertical.testclasses.NoIllegalClassAccessWithAccessModificationsTestClasses;
import com.android.tools.r8.classmerging.vertical.testclasses.NoIllegalClassAccessWithAccessModificationsTestClasses.SimpleInterfaceFactory;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.VerticallyMergedClassesInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NoIllegalClassAccessWithAccessModificationsTest extends VerticalClassMergerTestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestBase.getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public NoIllegalClassAccessWithAccessModificationsTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(NoIllegalClassAccessWithAccessModificationsTest.class)
        .addInnerClasses(NoIllegalClassAccessWithAccessModificationsTestClasses.class)
        .addKeepMainRule(TestClass.class)
        .addVerticallyMergedClassesInspector(this::inspectVerticallyMergedClasses)
        .allowAccessModification()
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccess();
  }

  private void inspectVerticallyMergedClasses(VerticallyMergedClassesInspector inspector) {
    inspector.assertMergedIntoSubtype(SimpleInterface.class, OtherSimpleInterface.class);
  }

  private void inspect(CodeInspector inspector) {
    assertThat(inspector.clazz(TestClass.class), isPresent());
    assertThat(inspector.clazz(getSimpleInterfaceImplClass()), isPresent());
    assertThat(inspector.clazz(OtherSimpleInterfaceImpl.class), isPresent());
    assertThat(inspector.clazz(SimpleInterface.class), not(isPresent()));
    assertThat(inspector.clazz(OtherSimpleInterface.class), not(isPresent()));
  }

  public static class TestClass {

    public static void main(String[] args) {
      // Without access modifications, it is not possible to merge the interface SimpleInterface
      // into
      // SimpleInterfaceImpl, since this would lead to an illegal class access here.
      SimpleInterface x = SimpleInterfaceFactory.create();
      x.foo();

      // Without access modifications, it is not possible to merge the interface
      // OtherSimpleInterface
      // into OtherSimpleInterfaceImpl, since this could lead to an illegal class access if another
      // package references OtherSimpleInterface.
      OtherSimpleInterface y = new OtherSimpleInterfaceImpl();
      y.bar();

      // Ensure that the instantiations are not dead code eliminated.
      escape(x);
      escape(y);
    }

    @NeverInline
    static void escape(Object o) {
      if (System.currentTimeMillis() < 0) {
        System.out.println(o);
      }
    }
  }

  // Should only be merged into OtherSimpleInterfaceImpl if access modifications are allowed.
  public interface SimpleInterface {

    void foo();
  }

  // Should only be merged into OtherSimpleInterfaceImpl if access modifications are allowed.
  public interface OtherSimpleInterface {

    void bar();
  }

  private static class OtherSimpleInterfaceImpl implements OtherSimpleInterface {

    @Override
    public void bar() {
      System.out.println("In bar on OtherSimpleInterfaceImpl");
    }
  }
}
