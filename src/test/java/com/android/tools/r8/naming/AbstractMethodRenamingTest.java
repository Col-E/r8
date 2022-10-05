// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AbstractMethodRenamingTest extends TestBase {

  static abstract class Base implements Runnable {
    @NeverInline
    public abstract void foo();

    @NeverInline
    @Override
    public void run() {
      foo();
    }
  }

  @NoHorizontalClassMerging
  static final class Sub1 extends Base {
    public final void foo() {
      System.out.println("Sub1::foo");
    }
  }

  @NoHorizontalClassMerging
  static final class Sub2 extends Base {
    public final void foo() {
      System.out.println("Sub2::foo");
    }
  }

  static class TestMain {
    static Runnable createInstance() {
      return System.currentTimeMillis() > 0 ? new Sub1() : new Sub2();
    }
    public static void main(String... args) {
      Runnable instance = createInstance();
      instance.run();
    }
  }

  private TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public AbstractMethodRenamingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(AbstractMethodRenamingTest.class)
        .addKeepMainRule(TestMain.class)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), TestMain.class)
        .assertSuccessWithOutput(StringUtils.lines("Sub1::foo"))
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject base = inspector.clazz(Base.class);
    assertThat(base, isPresentAndRenamed());
    MethodSubject foo = base.uniqueMethodWithOriginalName("foo");
    assertThat(foo, isPresentAndRenamed());

    ClassSubject sub1 = inspector.clazz(Sub1.class);
    assertThat(sub1, isPresentAndRenamed());
    MethodSubject fooInSub1 = sub1.uniqueMethodWithOriginalName("foo");
    assertThat(fooInSub1, isPresentAndRenamed());
    assertEquals(foo.getFinalName(), fooInSub1.getFinalName());

    ClassSubject sub2 = inspector.clazz(Sub1.class);
    assertThat(sub2, isPresentAndRenamed());
    MethodSubject fooInSub2 = sub2.uniqueMethodWithOriginalName("foo");
    assertThat(fooInSub2, isPresentAndRenamed());
    assertEquals(foo.getFinalName(), fooInSub2.getFinalName());
  }
}
