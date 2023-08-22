// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.sealed;

import static com.android.tools.r8.ToolHelper.getFilesInTestFolderRelativeToClass;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SealedInterfaceSubClassesHorizontalMergeTest extends KotlinTestBase {

  private static final String MAIN =
      "com.android.tools.r8.kotlin.sealed.kt.SealedInterfaceSubClassesKt";
  private static final String A =
      "com.android.tools.r8.kotlin.sealed.kt.SealedInterfaceSubClasses$A";
  private static final String B =
      "com.android.tools.r8.kotlin.sealed.kt.SealedInterfaceSubClasses$B";

  private static final String[] EXPECTED = new String[] {"an A: I am A", "a B: I am B"};

  private final TestParameters parameters;
  private final boolean clinitNoSideEffects;

  @Parameters(name = "{0}, {1}, clinitNoSideEffects: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters()
            // Sealed interfaces are supported from Kotlin 1.5.
            .withCompilersStartingFromIncluding(KotlinCompilerVersion.KOTLINC_1_5_0)
            .withOldCompilersStartingFrom(KotlinCompilerVersion.KOTLINC_1_5_0)
            .withAllTargetVersions()
            .build(),
        BooleanUtils.values());
  }

  public SealedInterfaceSubClassesHorizontalMergeTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean clinitNoSideEffects) {
    super(kotlinParameters);
    this.parameters = parameters;
    this.clinitNoSideEffects = clinitNoSideEffects;
  }

  private static final KotlinCompileMemoizer compilationResults =
      getCompileMemoizer(getKotlinSources());

  private static Collection<Path> getKotlinSources() {
    try {
      return getFilesInTestFolderRelativeToClass(
          SealedInterfaceSubClassesHorizontalMergeTest.class, "kt", "SealedInterfaceSubClasses.kt");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void testRuntime() throws ExecutionException, CompilationFailedException, IOException {
    assumeTrue(clinitNoSideEffects);
    testForRuntime(parameters)
        .addProgramFiles(compilationResults.getForConfiguration(kotlinc, targetVersion))
        .addRunClasspathFiles(buildOnDexRuntime(parameters, kotlinc.getKotlinStdlibJar()))
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramFiles(compilationResults.getForConfiguration(kotlinc, targetVersion))
        .addProgramFiles(kotlinc.getKotlinStdlibJar())
        .addProgramFiles(kotlinc.getKotlinAnnotationJar())
        .setMinApi(parameters)
        .allowDiagnosticWarningMessages()
        .addKeepMainRule(MAIN)
        .applyIf(
            clinitNoSideEffects,
            b ->
                b.addKeepRules("-assumenosideeffects class " + A + " { <clinit>(); }")
                    .addKeepRules("-assumenosideeffects class " + B + " { <clinit>(); }")
                    .addHorizontallyMergedClassesInspector(
                        inspector ->
                            inspector
                                .assertIsCompleteMergeGroup(A, B)
                                .assertNoOtherClassesMerged()),
            // TODO(b/296852026): Updates to horizintal class merging can cause this to start
            // failing as the classes can actually bne merged without -assumenosideeffects rules.
            b ->
                b.addHorizontallyMergedClassesInspector(
                    HorizontallyMergedClassesInspector::assertNoClassesMerged))
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines(EXPECTED);
  }
}
