// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedarguments;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedArgumentsObjectTest extends UnusedArgumentsTestBase {

  public UnusedArgumentsObjectTest(boolean minification) {
    super(minification);
  }

  @Parameters(name = "minification:{0}")
  public static Collection<Object[]> data() {
    return UnusedArgumentsTestBase.data();
  }

  static class TestObject {
    public final String s;

    TestObject(String s) {
      this.s = s;
    }

    @Override
    public String toString() {
      return s;
    }
  }

  @NeverClassInline
  static class TestClass {

    @NeverInline
    public static Object a(Object a) {
      return a;
    }

    @NeverInline
    public static Object a(Object a, Object b) {
      return a;
    }

    @NeverInline
    public static Object a(Object a, Object b, Object c) {
      return a;
    }

    @NeverInline
    private Object b(Object a) {
      return a;
    }

    @NeverInline
    private Object b(Object a, Object b) {
      return a;
    }

    @NeverInline
    private Object b(Object a, Object b, Object c) {
      return a;
    }

    public static void main(String[] args) {
      TestClass instance = new TestClass();
      System.out.print(a(new TestObject("1")));
      System.out.print(a(new TestObject("2"), new TestObject("3")));
      System.out.print(a(new TestObject("4"), new TestObject("5"), new TestObject("6")));
      System.out.print(instance.b(new TestObject("1")));
      System.out.print(instance.b(new TestObject("2"), new TestObject("3")));
      System.out.print(instance.b(new TestObject("4"), new TestObject("5"), new TestObject("6")));
    }
  }

  @Override
  public Class<?> getTestClass() {
    return TestClass.class;
  }

  @Override
  public Collection<Class<?>> getAdditionalClasses() {
    return ImmutableList.of(TestObject.class);
  }

  @Override
  public String getExpectedResult() {
    return "124124";
  }

  @Override
  public void inspectTestClass(ClassSubject clazz) {
    assertEquals(8, clazz.allMethods().size());
    clazz.forAllMethods(
        method ->
            Assert.assertTrue(
                method.getFinalName().equals("main")
                    || method.getFinalName().equals("<init>")
                    || (method.getFinalSignature().parameters.length == 1
                        && method.getFinalSignature().parameters[0].equals("java.lang.Object"))));
  }
}
