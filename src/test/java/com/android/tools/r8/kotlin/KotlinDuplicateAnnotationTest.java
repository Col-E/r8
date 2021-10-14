// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import java.util.Collection;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KotlinDuplicateAnnotationTest extends AbstractR8KotlinTestBase {

  private static final String FOLDER = "duplicate_annotation";
  private static final String MAIN = FOLDER + ".MainKt";
  private static final String KEEP_RULES = StringUtils.lines(
      "-keep,allowobfuscation @interface **.TestAnnotation",
      "-keepattributes *Annotations*"
  );

  private Consumer<InternalOptions> optionsModifier =
      o -> {
        o.inlinerOptions().enableInlining = false;
      };

  @Parameterized.Parameters(name = "{0}, {1}, allowAccessModification: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values());
  }

  private final TestParameters parameters;

  public KotlinDuplicateAnnotationTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean allowAccessModification) {
    super(parameters, kotlinParameters, allowAccessModification);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer compiledJars =
      getCompileMemoizer(getKotlinFilesInResource(FOLDER), FOLDER)
          .configure(kotlinCompilerTool -> kotlinCompilerTool.includeRuntime().noReflect());

  @Test
  public void test_dex() {
    assumeTrue(
        "kotlinc > 1.3.72 will no longer compile kotlin files with duplicate annotations",
        kotlinc.is(KotlinCompilerVersion.KOTLINC_1_3_72));
    assumeTrue("test DEX", parameters.isDexRuntime());
    try {
      testForR8(parameters.getBackend())
          .addProgramFiles(compiledJars.getForConfiguration(kotlinc, targetVersion))
          .addKeepMainRule(MAIN)
          .addKeepRules(KEEP_RULES)
          .noMinification()
          .addOptionsModification(optionsModifier)
          .compile();
      fail("Expect to fail");
    } catch (CompilationFailedException e) {
      assertThat(e.getCause().getMessage(), containsString("Multiple annotations"));
      assertThat(e.getCause().getMessage(), containsString("TestAnnotation"));
    }
  }

  @Test
  public void test_cf() throws Exception {
    assumeTrue(
        "kotlinc > 1.3.72 will no longer compile kotlin files with duplicate annotations",
        kotlinc.is(KotlinCompilerVersion.KOTLINC_1_3_72));
    assumeTrue("test CF", parameters.isCfRuntime());
    testForR8(parameters.getBackend())
        .addProgramFiles(compiledJars.getForConfiguration(kotlinc, targetVersion))
        .addKeepMainRule(MAIN)
        .addKeepRules(KEEP_RULES)
        .noMinification()
        .addOptionsModification(optionsModifier)
        .compile()
        .assertNoMessages();
  }

}
