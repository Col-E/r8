// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPublic;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InnerClassAttributePublicizerTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InnerClassAttributePublicizerTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addInnerClasses(InnerClassAttributePublicizerTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .allowAccessModification()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(Outer.Inner.class);
    assertThat(classSubject, isPresent());
    assertThat(classSubject, isPublic());

    InnerClassAttribute innerClassAttribute =
        classSubject.getDexProgramClass().getInnerClassAttributeForThisClass();
    assertNotNull(innerClassAttribute);

    ClassAccessFlags accessFlags =
        ClassAccessFlags.fromSharedAccessFlags(innerClassAttribute.getAccess());
    assertTrue(accessFlags.isPublic());
    assertFalse(accessFlags.isPrivate());
    assertFalse(accessFlags.isProtected());
  }

  static class TestClass {

    public static void main(String[] args) {
      new Outer();
      new Outer.Inner();
    }
  }

  static class Outer {

    static {
      System.out.print("Hello");
    }

    static class Inner {

      static {
        System.out.println(" world!");
      }
    }
  }
}
