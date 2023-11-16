// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.b139991218;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.KotlinCompilerTool.KotlinTargetVersion;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * This is a reproduction of b/139991218 where a defined annotation can no longer be found because
 * the hash has changed from when it was cached, due to the GenericSignatureRewriter.
 *
 * <p>It requires that the hash is pre-computed and that seem to happen only in the lambda merger,
 * which is why the ASMified code is generated from kotlin.
 */
@RunWith(Parameterized.class)
public class TestRunner extends KotlinTestBase {

  private final TestParameters parameters;

  private static final KotlinCompileMemoizer kotlinJars =
      getCompileMemoizer(getKotlinFilesInResource("lambdas_kstyle_generics"))
          // TODO(b/185465199): This is not really the test for testing shrinking reflect.
          .configure(kotlinCompilerTool -> kotlinCompilerTool.includeRuntime().noReflect());

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getKotlinTestParameters()
            .withAllCompilers()
            .withTargetVersion(KotlinTargetVersion.JAVA_8)
            .build());
  }

  public TestRunner(TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  @Test
  public void testSignatureRewriteHash()
      throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(Lambda1.dump(), Lambda2.dump(), Main.dump(), Alpha.dump())
        .addProgramFiles(
            kotlinJars.getForConfiguration(kotlinParameters), kotlinc.getKotlinAnnotationJar())
        .addKeepMainRule(Main.class)
        .addKeepAllAttributes()
        .addOptionsModification(options -> options.enableClassInlining = false)
        .addOptionsModification(InlinerOptions::setOnlyForceInlining)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(
                    "com.android.tools.r8.naming.b139991218.Lambda1",
                    "com.android.tools.r8.naming.b139991218.Lambda2"))
        .allowDiagnosticWarningMessages()
        .setMinApi(parameters)
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("11", "12")
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz("com.android.tools.r8.naming.b139991218.Lambda1"),
                    isPresent()));
  }
}
