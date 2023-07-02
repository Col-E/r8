// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.sideeffect;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SingletonClassInitializerWithInstancePutCannotBePostponedTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SingletonClassInitializerWithInstancePutCannotBePostponedTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoAccessModificationAnnotationsForMembers()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(A.class);
    assertThat(classSubject, isPresent());
    assertThat(classSubject.uniqueFieldWithOriginalName("INSTANCE"), isPresent());

    // A.inlineable() should be inlined, but we should synthesize an $r8$clinit field.
    assertThat(classSubject.uniqueMethodWithOriginalName("inlineable"), not(isPresent()));
    assertEquals(2, classSubject.allStaticFields().size());
  }

  static class Main {

    public static void main(String[] args) {
      Environment.data = " world!";
      // Triggers A.<clinit>(), which sets A.INSTANCE to a new instance with A.message=" world".
      A.inlineable();
      // Unset Environment.data, such that the following call to println() prints null if we failed
      // to trigger A.<clinit>() above.
      Environment.data = null;
      System.out.println(A.getInstance().getMessage());
    }
  }

  @NeverClassInline
  static class A {

    @NoAccessModification private static A INSTANCE;

    static {
      A a = new A();
      a.message = Environment.data;
      INSTANCE = a;
    }

    @NeverPropagateValue private String message;

    static void inlineable() {
      System.out.print("Hello");
    }

    @NeverInline
    static A getInstance() {
      return INSTANCE;
    }

    @NeverInline
    String getMessage() {
      return message;
    }
  }

  static class Environment {

    static String data;
  }
}
