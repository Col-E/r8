// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KotlinIntrinsicsInlineTest extends AbstractR8KotlinTestBase {
  private static final String FOLDER = "intrinsics";
  private static final String MAIN = FOLDER + ".InlineKt";

  @Parameterized.Parameters(name = "{0} target: {1}, allowAccessModification: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().build(),
        KotlinTargetVersion.values(),
        BooleanUtils.values());
  }

  private final TestParameters parameters;

  public KotlinIntrinsicsInlineTest(
      TestParameters parameters,
      KotlinTargetVersion targetVersion,
      boolean allowAccessModification) {
    super(targetVersion, allowAccessModification);
    this.parameters = parameters;
  }

  @Test
  public void b139432507() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(getKotlinJarFile(FOLDER))
        .addKeepRules(StringUtils.lines(
            "-keepclasseswithmembers class " + MAIN + "{",
            "  public static *** *(...);",
            "}"))
        .allowAccessModification(allowAccessModification)
        .noMinification()
        .setMinApi(parameters.getRuntime())
        .compile()
        .inspect(inspector -> {
          ClassSubject main = inspector.clazz(MAIN);
          assertThat(main, isPresent());

          // Note that isSupported itself has a parameter whose null check would be inlined
          // with -allowaccessmodification.
          MethodSubject isSupported = main.uniqueMethodWithName("isSupported");
          assertThat(isSupported, isPresent());
          assertEquals(
              allowAccessModification ? 0 : 1,
              countCall(isSupported, "checkParameterIsNotNull"));

          // In general cases, null check won't be invoked only once or twice, hence no subtle
          // situation in double inlining.
          MethodSubject containsArray = main.uniqueMethodWithName("containsArray");
          assertThat(containsArray, isPresent());
          assertEquals(0, countCall(containsArray, "checkParameterIsNotNull"));
        });
  }

  @Test
  public void b139432507_isSupported() throws Exception {
    assumeTrue("Different inlining behavior on CF backend", parameters.isDexRuntime());
    testSingle("isSupported");
  }

  @Test
  public void b139432507_containsArray() throws Exception {
    assumeTrue("Different inlining behavior on CF backend", parameters.isDexRuntime());
    testSingle("containsArray");
  }

  private void testSingle(String methodName) throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(getKotlinJarFile(FOLDER))
        .addKeepRules(StringUtils.lines(
            "-keepclasseswithmembers class " + MAIN + "{",
            "  public static *** " + methodName + "(...);",
            "}"))
        .allowAccessModification(allowAccessModification)
        .noMinification()
        .setMinApi(parameters.getRuntime())
        .compile()
        .inspect(inspector -> {
          ClassSubject main = inspector.clazz(MAIN);
          assertThat(main, isPresent());

          MethodSubject method = main.uniqueMethodWithName(methodName);
          assertThat(method, isPresent());
          int arity = method.getMethod().method.getArity();
          // One from the method's own argument, if any, and
          // Two from Array utils, `contains` and `indexOf`, if inlined with access relaxation.
          assertEquals(
              allowAccessModification ? 0 : arity + 2,
              countCall(method, "checkParameterIsNotNull"));
        });
  }
}
