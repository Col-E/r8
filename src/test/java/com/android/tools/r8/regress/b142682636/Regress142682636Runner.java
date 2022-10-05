// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b142682636;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexMoveWide;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress142682636Runner extends TestBase {
  private static final Class<?> testClass = Regress142682636.class;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;

  public Regress142682636Runner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    CodeInspector inspector = testForD8()
        .addProgramClasses(testClass)
        .setMinApi(parameters.getApiLevel())
        .release()
        .compile()
        .inspector();
    ClassSubject clazz = inspector.clazz(testClass);
    assertThat(clazz, isPresent());
    MethodSubject foo = clazz.uniqueMethodWithOriginalName("foo");
    assertThat(foo, isPresent());
    checkNoMoveWide(foo);
    MethodSubject bar = clazz.uniqueMethodWithOriginalName("bar");
    assertThat(bar, isPresent());
    checkNoMoveWide(bar);
  }

  private void checkNoMoveWide(MethodSubject m) {
    assertTrue(
        Arrays.stream(m.getMethod().getCode().asDexCode().instructions)
            .noneMatch(i -> i instanceof DexMoveWide));
  }

}
