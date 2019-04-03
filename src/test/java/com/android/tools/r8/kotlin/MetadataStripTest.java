// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataStripTest extends KotlinTestBase {
  private static final String METADATA_DESCRIPTOR = "Lkotlin/Metadata;";
  private static final String KEEP_ANNOTATIONS = "-keepattributes *Annotation*";

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataStripTest(TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  @Test
  public void testJstyleRunnable() throws Exception {
    final String folder = "lambdas_jstyle_runnable";
    final String mainClassName = "lambdas_jstyle_runnable.MainKt";
    final String implementer1ClassName = "lambdas_jstyle_runnable.Implementer1Kt";
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramFiles(getKotlinJarFile(folder))
            .addProgramFiles(getJavaJarFile(folder))
            .addProgramFiles(ToolHelper.getKotlinReflectJar())
            .enableInliningAnnotations()
            .addKeepMainRule(mainClassName)
            .addKeepRules(KEEP_ANNOTATIONS)
            .addKeepRules("-keep class kotlin.Metadata")
            .run(parameters.getRuntime(), mainClassName);
    CodeInspector inspector = result.inspector();
    ClassSubject clazz = inspector.clazz(mainClassName);
    assertThat(clazz, isPresent());
    assertThat(clazz, not(isRenamed()));
    // Main class is kept, hence the presence of Metadata.
    assertNotNull(retrieveMetadata(clazz.getDexClass()));
    ClassSubject impl1 = inspector.clazz(implementer1ClassName);
    assertThat(impl1, isPresent());
    assertThat(impl1, isRenamed());
    // All other classes can be renamed, hence the absence of Metadata;
    assertNull(retrieveMetadata(impl1.getDexClass()));
  }

  private DexAnnotation retrieveMetadata(DexClass dexClass) {
    for (DexAnnotation annotation : dexClass.annotations.annotations) {
      if (annotation.annotation.type.toDescriptorString().equals(METADATA_DESCRIPTOR)) {
        return annotation;
      }
    }
    return null;
  }

}
