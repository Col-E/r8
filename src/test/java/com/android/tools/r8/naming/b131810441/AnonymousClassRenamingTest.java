// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.b131810441;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.naming.b131810441.sub.Outer;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

class TestMain {
  public static void main(String... args) {
    Outer o = Outer.create(8);
    o.trigger();
  }
}

@RunWith(Parameterized.class)
public class AnonymousClassRenamingTest extends TestBase {
  private static final String EXPECTED_OUTPUT = StringUtils.lines("Outer#<init>(8)");

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enableMinification;

  @Parameterized.Parameters(name = "{0} minification: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  @Test
  public void b131810441() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addProgramClasses(TestMain.class)
        .addProgramClassesAndInnerClasses(Outer.class)
        .addKeepMainRule(TestMain.class)
        .addKeepAttributes("InnerClasses", "EnclosingMethod")
        .enableInliningAnnotations()
        .minification(enableMinification)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestMain.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(
            inspector -> {
              ClassSubject anonymous = inspector.clazz(Outer.class.getName() + "$1");
              assertThat(anonymous, isPresent());
              assertEquals(enableMinification, anonymous.isRenamed());
            });
  }
}
