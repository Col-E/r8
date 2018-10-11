// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.verticalclassmerging;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class MergedTypeBaseTest extends TestBase {

  private final List<Class> CLASSES = ImmutableList.of(A.class, B.class, C.class, getTestClass());

  static class A {}

  static class B extends A {}

  static class C {}

  private final Backend backend;
  private final boolean enableVerticalClassMerging;

  public MergedTypeBaseTest(Backend backend, boolean enableVerticalClassMerging) {
    this.backend = backend;
    this.enableVerticalClassMerging = enableVerticalClassMerging;
  }

  @Parameters(name = "Backend: {0}, vertical class merging: {1}")
  public static Collection<Object[]> data() {
    // We don't run this on Proguard, as Proguard does not merge A into B.
    return ImmutableList.of(
        new Object[] {Backend.DEX, true},
        new Object[] {Backend.DEX, false},
        new Object[] {Backend.CF, true},
        new Object[] {Backend.CF, false});
  }

  public abstract Class<?> getTestClass();

  public abstract String getConditionForProguardIfRule();

  public abstract String getExpectedStdout();

  @Test
  public void testIfRule() throws Exception {
    String expected = getExpectedStdout();
    assertEquals(expected, runOnJava(getTestClass()));

    String config =
        StringUtils.joinLines(
            "-keep class " + getTestClass().getTypeName() + " {",
            "  public static void main(java.lang.String[]);",
            "}",
            getConditionForProguardIfRule(),
            "-keep class " + C.class.getTypeName());
    AndroidApp output = compileWithR8(readClasses(CLASSES), config, this::configure, backend);
    assertEquals(expected, runOnVM(output, getTestClass(), backend));

    CodeInspector inspector = new CodeInspector(output);
    assertThat(inspector.clazz(MergedReturnTypeTest.C.class), isPresent());
  }

  private void configure(InternalOptions options) {
    options.enableMinification = false;
    options.enableVerticalClassMerging = enableVerticalClassMerging;

    // TODO(b/110148109): Allow ordinary method inlining when -if rules work with inlining.
    options.testing.validInliningReasons = ImmutableSet.of(Reason.FORCE);
  }
}
