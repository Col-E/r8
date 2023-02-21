// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.VerticallyMergedClassesInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassesHaveBeenMergedTest extends VerticalClassMergerTestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestBase.getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassesHaveBeenMergedTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testClassesHaveBeenMerged() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ClassesHaveBeenMergedTest.class)
        .addKeepMainRule(TestClass.class)
        .addVerticallyMergedClassesInspector(this::inspectVerticallyMergedClasses)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccess();
  }

  private void inspectVerticallyMergedClasses(VerticallyMergedClassesInspector inspector) {
    inspector.assertMergedIntoSubtype(
        GenericInterface.class,
        GenericAbstractClass.class,
        Outer.SuperClass.class,
        SuperClass.class);
  }

  private void inspect(CodeInspector inspector) {
    assertThat(inspector.clazz(GenericInterfaceImpl.class), isPresent());
    assertThat(inspector.clazz(Outer.SubClass.class), isPresent());
    assertThat(inspector.clazz(SubClass.class), isPresent());
    assertThat(inspector.clazz(GenericInterface.class), not(isPresent()));
    assertThat(inspector.clazz(GenericAbstractClass.class), not(isPresent()));
    assertThat(inspector.clazz(Outer.SuperClass.class), not(isPresent()));
    assertThat(inspector.clazz(SuperClass.class), not(isPresent()));
  }

  public static class TestClass {

    public static void main(String... args) {
      GenericInterface<?> iface = new GenericInterfaceImpl();
      callMethodOnIface(iface);
      GenericAbstractClass<?> clazz = new GenericAbstractClassImpl();
      callMethodOnAbstractClass(clazz);
      Outer outer = new Outer();
      Outer.SubClass inner = outer.getInstance();
      System.out.println(outer.getInstance().method());
      System.out.println(new SubClass(42));

      // Ensure that the instantiations are not dead code eliminated.
      escape(clazz);
      escape(iface);
      escape(inner);
      escape(outer);
    }

    private static void callMethodOnIface(GenericInterface<?> iface) {
      System.out.println(iface.method());
    }

    private static void callMethodOnAbstractClass(GenericAbstractClass<?> clazz) {
      System.out.println(clazz.method());
      System.out.println(clazz.otherMethod());
    }

    @NeverInline
    static void escape(Object o) {
      if (System.currentTimeMillis() < 0) {
        System.out.println(o);
      }
    }
  }

  public abstract static class GenericAbstractClass<T> {

    public abstract T method();

    public T otherMethod() {
      return null;
    }
  }

  public static class GenericAbstractClassImpl extends GenericAbstractClass<String> {

    @Override
    public String method() {
      return "Hello from GenericAbstractClassImpl";
    }

    @Override
    public String otherMethod() {
      return "otherMethod";
    }
  }

  public interface GenericInterface<T> {

    T method();
  }

  @NoHorizontalClassMerging
  public static class GenericInterfaceImpl implements GenericInterface<String> {

    @Override
    public String method() {
      return "method";
    }
  }

  public static class SuperClass {

    private final int field;

    public SuperClass(int field) {
      this.field = field;
    }

    public int getField() {
      return field;
    }
  }

  public static class SubClass extends SuperClass {

    private int field;

    public SubClass(int field) {
      this(field, field + 100);
    }

    public SubClass(int one, int other) {
      super(one);
      field = other;
    }

    public String toString() {
      return "is " + field + " " + getField();
    }
  }

  static class Outer {

    /**
     * This class is package private to trigger the generation of bridge methods for the visibility
     * change of methods from public subtypes.
     */
    static class SuperClass {

      public String method() {
        return "Method in SuperClass.";
      }
    }

    @NoHorizontalClassMerging
    public static class SubClass extends SuperClass {
      // Intentionally left empty.
    }

    public SubClass getInstance() {
      return new SubClass();
    }
  }
}
