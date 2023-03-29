// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_3_72;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_7_0;

import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ProcessKotlinReflectionLibTest extends KotlinTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public ProcessKotlinReflectionLibTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private void test(ThrowableConsumer<R8FullTestBuilder> testBuilderConsumer) throws Exception {
    test(testBuilderConsumer, ThrowableConsumer.empty());
  }

  private void test(
      ThrowableConsumer<R8FullTestBuilder> testBuilderConsumer,
      ThrowableConsumer<R8TestCompileResult> compileResultBuilder)
      throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryFiles(
            ToolHelper.getMostRecentAndroidJar(),
            kotlinc.getKotlinStdlibJar(),
            kotlinc.getKotlinAnnotationJar())
        .addProgramFiles(kotlinc.getKotlinReflectJar())
        .addKeepAttributes(ProguardKeepAttributes.SIGNATURE)
        .addKeepAttributes(ProguardKeepAttributes.INNER_CLASSES)
        .addKeepAttributes(ProguardKeepAttributes.ENCLOSING_METHOD)
        .allowDiagnosticMessages()
        .allowUnusedDontWarnKotlinReflectJvmInternal(kotlinc.isNot(KOTLINC_1_3_72))
        .allowUnusedDontWarnJavaLangClassValue(
            kotlinc.getCompilerVersion().isGreaterThan(KOTLINC_1_7_0))
        .allowUnusedProguardConfigurationRules(
            kotlinc.getCompilerVersion().isGreaterThan(KOTLINC_1_3_72))
        .apply(testBuilderConsumer)
        .apply(configureForLibraryWithEmbeddedProguardRules())
        .compile()
        .apply(compileResultBuilder)
        .apply(assertUnusedKeepRuleForKotlinMetadata(kotlinc.isNot(KOTLINC_1_3_72)));
  }

  @Test
  public void testAsIs() throws Exception {
    test(builder -> builder.addDontObfuscate().addDontOptimize().noTreeShaking());
  }

  @Test
  public void testDontShrinkAndDontOptimize() throws Exception {
    test(builder -> builder.addDontOptimize().noTreeShaking());
  }

  @Test
  public void testDontShrinkAndDontObfuscate() throws Exception {
    test(builder -> builder.addDontObfuscate().noTreeShaking());
  }

  @Test
  public void testDontShrink() throws Exception {
    test(TestShrinkerBuilder::noTreeShaking);
  }

  @Test
  public void testDontShrinkDifferently() throws Exception {
    test(
        builder ->
            builder
                .addKeepRules("-keep,allowobfuscation class **.*KClasses*")
                .noTreeShaking());
  }

  @Test
  public void testDontOptimize() throws Exception {
    test(TestShrinkerBuilder::addDontOptimize);
  }

  @Test
  public void testDontObfuscate() throws Exception {
    test(TestShrinkerBuilder::addDontObfuscate);
  }

}
