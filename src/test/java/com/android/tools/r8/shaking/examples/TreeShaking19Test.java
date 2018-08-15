// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase.MinifyMode;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShaking19Test extends TreeShakingTest {

  @Parameters(name = "mode:{0}-{1} minify:{2}")
  public static Collection<Object[]> data() {
    List<Object[]> parameters = new ArrayList<>();
    for (MinifyMode minify : MinifyMode.values()) {
      parameters.add(new Object[] {Frontend.JAR, Backend.CF, minify});
      parameters.add(new Object[] {Frontend.JAR, Backend.DEX, minify});
      parameters.add(new Object[] {Frontend.DEX, Backend.DEX, minify});
    }
    return parameters;
  }

  public TreeShaking19Test(Frontend frontend, Backend backend, MinifyMode minify) {
    super("examples/shaking19", "shaking19.Shaking", frontend, backend, minify);
  }

  @Ignore("b/111199171")
  @Test
  public void test() throws Exception {
    runTest(
        TreeShaking19Test::unusedRemoved,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking19/keep-rules.txt"),
        // Disable vertical class merging to prevent A from being merged into B.
        opt -> opt.enableVerticalClassMerging = false);
  }

  private static void unusedRemoved(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz("shaking19.Shaking$A");
    assertThat(clazz, isPresent());

    MethodSubject method = clazz.method("void", "m", ImmutableList.of());
    assertThat(method, not(isPresent()));
  }
}
