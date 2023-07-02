// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedarguments;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UnusedArgumentsObjectTest extends UnusedArgumentsTestBase {

  public UnusedArgumentsObjectTest(TestParameters parameters, boolean minification) {
    super(parameters, minification);
  }

  @NeverClassInline
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
    public static Object publicStaticMethod(Object a) {
      return a;
    }

    @NeverInline
    public static Object publicStaticMethod(Object a, Object b) {
      return a;
    }

    @NeverInline
    public static Object publicStaticMethod(Object a, Object b, Object c) {
      return a;
    }

    @NeverInline
    @NoAccessModification
    @NoMethodStaticizing
    private Object privateMethod(Object a) {
      return a;
    }

    @NeverInline
    @NoAccessModification
    @NoMethodStaticizing
    private Object privateMethod(Object a, Object b) {
      return a;
    }

    @NeverInline
    @NoAccessModification
    @NoMethodStaticizing
    private Object privateMethod(Object a, Object b, Object c) {
      return a;
    }

    @NeverInline
    @NoMethodStaticizing
    public Object publicMethod(Object a) {
      return a;
    }

    @NeverInline
    @NoMethodStaticizing
    public Object publicMethod(Object a, Object b) {
      return a;
    }

    @NeverInline
    @NoMethodStaticizing
    public Object publicMethod(Object a, Object b, Object c) {
      return a;
    }

    public static void main(String[] args) {
      TestClass instance = new TestClass();
      System.out.print(publicStaticMethod(new TestObject("1")));
      System.out.print(publicStaticMethod(new TestObject("2"), new TestObject("3")));
      System.out.print(
          publicStaticMethod(new TestObject("4"), new TestObject("5"), new TestObject("6")));
      System.out.print(instance.privateMethod(new TestObject("1")));
      System.out.print(instance.privateMethod(new TestObject("2"), new TestObject("3")));
      System.out.print(
          instance.privateMethod(new TestObject("4"), new TestObject("5"), new TestObject("6")));
      System.out.print(instance.publicMethod(new TestObject("1")));
      System.out.print(instance.publicMethod(new TestObject("2"), new TestObject("3")));
      System.out.print(
          instance.publicMethod(new TestObject("4"), new TestObject("5"), new TestObject("6")));
    }
  }

  @Override
  public void configure(R8FullTestBuilder builder) {
    builder
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .enableNoAccessModificationAnnotationsForMembers()
        .enableNoMethodStaticizingAnnotations();
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
    return "124124124";
  }

  @Override
  public void inspectTestClass(ClassSubject clazz) {
    assertEquals(
        parameters.canHaveNonReboundConstructorInvoke() ? 10 : 11, clazz.allMethods().size());
    clazz.forAllMethods(
        method ->
            Assert.assertTrue(
                method.getFinalName().equals("main")
                    || method.getFinalName().equals("<init>")
                    || (method.getFinalSignature().parameters.length == 1
                        && method.getFinalSignature().parameters[0].equals("java.lang.Object"))));
  }
}
