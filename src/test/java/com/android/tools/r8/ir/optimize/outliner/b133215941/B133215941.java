// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.outliner.b133215941;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.AssumeMayHaveSideEffects;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.optimize.outliner.b133215941.B133215941.TestClass.ClassWithStaticMethod;
import com.android.tools.r8.utils.InternalOptions.OutlineOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B133215941 extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public B133215941(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void validateOutlining(CodeInspector inspector) {
    ClassSubject outlineClass = inspector.clazz(OutlineOptions.CLASS_NAME);
    MethodSubject outline0Method =
        outlineClass.method(
            "void",
            "outline0",
            ImmutableList.of(TestClass.class.getTypeName(), TestClass.class.getTypeName()));
    assertThat(outline0Method, isPresent());
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(
        classSubject.uniqueMethodWithName("ifaceMethod"),
        CodeMatchers.invokesMethod(outline0Method));
    assertThat(
        classSubject.uniqueMethodWithName("methodWithOutlineContent"),
        CodeMatchers.invokesMethod(outline0Method));
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("Hello, world 5");
    testForR8(parameters.getBackend())
        .enableInliningAnnotations()
        .enableClassInliningAnnotations()
        .enableMergeAnnotations()
        .enableSideEffectAnnotations()
        .addInnerClasses(B133215941.class)
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(ClassWithStaticMethod.class)
        .setMinApi(parameters.getRuntime())
        .noMinification()
        .addOptionsModification(options -> {
          if (parameters.isCfRuntime()) {
            assert !options.outline.enabled;
            options.outline.enabled = true;
          }
          options.outline.threshold = 2;
        })
        .compile()
        .inspect(this::validateOutlining)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  @NeverMerge
  public interface Iface {
    void ifaceMethod();
  }

  @NeverMerge
  public static class TestClassSuper {
    @AssumeMayHaveSideEffects
    @NeverInline
    public void superMethod() {}
  }

  @NeverClassInline
  public static class TestClass extends TestClassSuper implements Iface {

    @NeverInline
    public void ifaceMethod() {
      // These three invokes are expected to be outlined.
      ClassWithStaticMethod.staticMethodWithInterfaceArgumentType(this);
      superMethod();
      ClassWithStaticMethod.staticMethodWithInterfaceArgumentType(this);
    }

    @NeverInline
    public void methodWithOutlineContent() {
      // These three invokes are expected to be outlined.
      ClassWithStaticMethod.staticMethodWithInterfaceArgumentType(this);
      superMethod();
      ClassWithStaticMethod.staticMethodWithInterfaceArgumentType(this);
    }

    @NeverClassInline
    static class AnotherClassImplementingIface implements Iface {
      @NeverInline
      public void ifaceMethod() {
        // Do nothing.
      }
    }

    public static class ClassWithStaticMethod {
      private static Iface iface;
      public static int count;

      @NeverInline
      public static void staticMethodWithInterfaceArgumentType(Iface iface) {
        ClassWithStaticMethod.iface = iface;
        count++;
      }
    }

    public static void main(String[] args) {
      TestClass tc = new TestClass();
      tc.methodWithOutlineContent();
      tc.ifaceMethod();
      ClassWithStaticMethod.staticMethodWithInterfaceArgumentType(
          new AnotherClassImplementingIface());
      System.out.println("Hello, world " + ClassWithStaticMethod.count);
    }
  }
}
