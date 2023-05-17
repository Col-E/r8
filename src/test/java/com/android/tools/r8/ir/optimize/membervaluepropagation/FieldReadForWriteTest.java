// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.optimize.membervaluepropagation.FieldReadForWriteTest.R.anim;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FieldReadForWriteTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withDefaultRuntimes().withAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .addOptionsModification(o -> o.testing.roundtripThroughLir = true)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("42")
        .inspect(inspector -> assertThat(inspector.clazz(anim.class), isAbsent()));
  }

  static class Main {

    public static void main(String[] args) {
      int packageId = args.length;
      R.onResourcesLoaded(packageId);
    }
  }

  @NoHorizontalClassMerging
  static class R {

    static boolean sResourcesDidLoad;

    @NoHorizontalClassMerging
    static class anim {
      public static int abc_fade_in = 0x7f010039;
    }

    static void onResourcesLoaded(int packageId) {
      if (sResourcesDidLoad) {
        return;
      }
      sResourcesDidLoad = true;
      int packageIdTransform = (packageId ^ 0x7f) << 24;
      onResourcesLoadedAnim(packageIdTransform);
    }

    static void onResourcesLoadedAnim(int packageIdTransform) {
      // Arithmethic binop (add, div, mul, rem, sub).
      anim.abc_fade_in += packageIdTransform;
      anim.abc_fade_in /= packageIdTransform;
      anim.abc_fade_in *= packageIdTransform;
      anim.abc_fade_in %= packageIdTransform;
      anim.abc_fade_in -= packageIdTransform;
      // Logical binop (and, or, shl, shr, ush, xor).
      anim.abc_fade_in &= packageIdTransform;
      anim.abc_fade_in |= packageIdTransform;
      anim.abc_fade_in <<= packageIdTransform;
      anim.abc_fade_in >>= packageIdTransform;
      anim.abc_fade_in >>>= packageIdTransform;
      anim.abc_fade_in ^= packageIdTransform;
      // Unop (number conversion, but also: inc, neg, not).
      anim.abc_fade_in = (int) ((long) anim.abc_fade_in);
      System.out.println("42");
    }
  }
}
