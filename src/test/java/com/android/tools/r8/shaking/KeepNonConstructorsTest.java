// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepNonConstructorsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void keepConstructorTest() throws Exception {
    testForR8(Backend.DEX)
        .addInnerClasses(getClass())
        .addKeepRules("-keep class " + A.class.getTypeName() + " { constructor *** *(...); }")
        .setMinApi(AndroidApiLevel.LATEST)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              assertThat(aClassSubject.init(), isPresent());
              assertEquals(1, aClassSubject.allMethods().size());
            });
  }

  @Test
  public void keepNonConstructorsTest() throws Exception {
    testForR8(Backend.DEX)
        .addInnerClasses(getClass())
        .addKeepRules("-keep class " + A.class.getTypeName() + " { !constructor *** *(...); }")
        .setMinApi(AndroidApiLevel.LATEST)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              assertThat(aClassSubject.uniqueMethodWithOriginalName("m"), isPresent());
              assertEquals(1, aClassSubject.allMethods().size());
            });
  }

  static class A {

    A() {}

    void m() {}
  }
}
