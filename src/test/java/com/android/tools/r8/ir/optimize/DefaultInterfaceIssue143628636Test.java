// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Reproduction for issue b/143628636.
 *
 * <p>There are a lot of conditions that need to be in place to trigger this issue. The root issue
 * is resolution incorrectly pruning out a valid candidate. However, mostly code will not even get
 * to that place as a single target is typically found prior to it, and then, if the incorrect
 * pruning of the lookup targets takes place, that needs to further cause invalid call site
 * information to be propagated, which in turn will cause inlining to inline a method where it
 * should not. It is exceedingly unlikely that this test will continue to be a regression test as
 * any one of the above aspects could and likely will be changed in the code base.
 */
@RunWith(Parameterized.class)
public class DefaultInterfaceIssue143628636Test extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public DefaultInterfaceIssue143628636Test(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .addInnerClasses(DefaultInterfaceIssue143628636Test.class)
        .addKeepMainRule(TestClass.class)
        .addKeepClassRules(I.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("2", "5");
  }

  @NoVerticalClassMerging
  @NeverClassInline
  public interface A {

    @NeverInline
    default void f(
        // This parameter is needed otherwise the info recording bails out. (See b/143684659).
        int z) {
      // In the end the issue will manifest as a class-cast exception because B.g() is inlined here
      // due to the incorrect conclusion that the only possible receiver type is B.
      System.out.println(g() + z);
    }

    int g();
  }

  // This intermediate interface is needed to cause lookupInterfaceTargets to return null as I
  // is pinned (why does it return null for a pinned holder is questionable, filed b/143686005).
  // The return of null, will cause the outer lookup to hit a different lookup case where the
  // refined receiver (here I) will cause the non-subtype method A.f to be pruned.
  public interface I extends A {}

  // Make sure this class and the call to h() are never eliminated. It is the *partial* info
  // propagated from h() to f() that results in incorrect call-site optimization info.
  @NoVerticalClassMerging
  @NeverClassInline
  public static class B implements A {
    public final int x;

    public B(int x) {
      this.x = x;
    }

    @Override
    public int g() {
      return x;
    }

    @NeverInline
    public void h() {
      // This will lookup A.f and propagate that the receiver is known to be B.
      f(x);
    }
  }

  // Make sure this class and the call to h() are never eliminated. It is the *missing* info
  // propagated from h() to f() that results in incorrect call-site optimization info.
  @NoVerticalClassMerging
  @NeverClassInline
  public static class C implements I {
    public final I i;
    public final int y;

    public C(I i, int y) {
      this.i = i;
      this.y = y;
    }

    @Override
    public int g() {
      return y;
    }

    @NeverInline
    public void h() {
      // Due to the refined receiver type I used here, the lookup targets will omit A.f !
      // Thus the info propagation does not propagate that I (and thus C) may be a receiver type.
      i.f(y);
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      new B(args.length + 1).h();
      new C(args.length == 42 ? null : new C(null, args.length + 2), args.length + 3).h();
    }
  }
}
