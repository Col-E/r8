// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.b133686361;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
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
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// Reproduce b/133686361
// This test is just same as {@link com.android.tools.r8.naming.AbstractMethodRenamingTest},
// except for methods `foo` being renamed to `a` (e.g., already processed by other shrinker).
// In that case, assigning `a` to Base#a seems not a new renaming. But, if we skip marking that
// name in the corresponding naming state, all subtypes' `a` are renamed to some other names,
// resulting in AbstractMethodError.
@RunWith(Parameterized.class)
public class AlreadyRenamedAbstractMethodRenamingTest extends TestBase {

  static abstract class Base implements Runnable {
    @NeverInline
    public abstract void a();

    @NeverInline
    @Override
    public void run() {
      a();
    }
  }

  @NoHorizontalClassMerging
  static final class Sub1 extends Base {
    public final void a() {
      System.out.println("Sub1::a");
    }
  }

  @NoHorizontalClassMerging
  static final class Sub2 extends Base {
    public final void a() {
      System.out.println("Sub2::a");
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

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void b133686361() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(AlreadyRenamedAbstractMethodRenamingTest.class)
        .addKeepMainRule(TestMain.class)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestMain.class)
        .assertSuccessWithOutput(StringUtils.lines("Sub1::a"))
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject base = inspector.clazz(Base.class);
    assertThat(base, isPresentAndRenamed());
    MethodSubject a = base.uniqueMethodWithOriginalName("a");
    assertThat(a, isPresentAndNotRenamed());

    ClassSubject sub1 = inspector.clazz(Sub1.class);
    assertThat(sub1, isPresentAndRenamed());
    MethodSubject aInSub1 = sub1.uniqueMethodWithOriginalName("a");
    assertThat(aInSub1, isPresentAndNotRenamed());
    assertEquals(a.getFinalName(), aInSub1.getFinalName());

    ClassSubject sub2 = inspector.clazz(Sub1.class);
    assertThat(sub2, isPresentAndRenamed());
    MethodSubject aInSub2 = sub2.uniqueMethodWithOriginalName("a");
    assertThat(aInSub2, isPresentAndNotRenamed());
    assertEquals(a.getFinalName(), aInSub2.getFinalName());
  }
}
