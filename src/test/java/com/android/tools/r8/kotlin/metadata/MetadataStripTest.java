// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_3_72;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_7_0;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.AnnotationSubject;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataStripTest extends KotlinMetadataTestBase {

  private final TestParameters parameters;
  private static final String FOLDER = "lambdas_jstyle_runnable";

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public MetadataStripTest(TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer compiledJars =
      getCompileMemoizer(getKotlinFilesInResource(FOLDER), FOLDER)
          .configure(kotlinCompilerTool -> kotlinCompilerTool.includeRuntime().noReflect());

  @Test
  public void testJstyleRunnable() throws Exception {
    final String mainClassName = "lambdas_jstyle_runnable.MainKt";
    final String implementer1ClassName = "lambdas_jstyle_runnable.Implementer1Kt";
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramFiles(compiledJars.getForConfiguration(kotlinc, targetVersion))
            .addProgramFiles(getJavaJarFile(FOLDER))
            .addProgramFiles(kotlinc.getKotlinReflectJar(), kotlinc.getKotlinAnnotationJar())
            .addKeepMainRule(mainClassName)
            .addKeepKotlinMetadata()
            .allowDiagnosticWarningMessages()
            .setMinApi(parameters)
            .allowUnusedDontWarnKotlinReflectJvmInternal(kotlinc.isNot(KOTLINC_1_3_72))
            .allowUnusedDontWarnJavaLangClassValue(
                kotlinc.getCompilerVersion().isGreaterThan(KOTLINC_1_7_0))
            .apply(configureForLibraryWithEmbeddedProguardRules())
            .compile()
            .assertNoErrorMessages()
            .apply(KotlinMetadataTestBase::verifyExpectedWarningsFromKotlinReflectAndStdLib)
            .run(parameters.getRuntime(), mainClassName);
    CodeInspector inspector = result.inspector();
    ClassSubject clazz = inspector.clazz(mainClassName);
    assertThat(clazz, isPresentAndNotRenamed());
    // Main class is kept, hence the presence of Metadata.
    AnnotationSubject annotationSubject = clazz.annotation(METADATA_TYPE);
    assertThat(annotationSubject, isPresent());
  }
}
