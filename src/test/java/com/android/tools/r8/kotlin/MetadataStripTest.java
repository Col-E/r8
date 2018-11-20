// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataStripTest extends TestBase {
  private static final String METADATA_DESCRIPTOR = "Lkotlin/Metadata;";
  private static final String KEEP_ANNOTATIONS = "-keepattributes *Annotation*";

  private final Backend backend;
  private final KotlinTargetVersion targetVersion;

  private Consumer<InternalOptions> optionsModifier =
      o -> {
        o.enableTreeShaking = true;
        o.enableMinification = true;
        // TODO(b/119626580): assertion failure at fixupStaticizedValueUsers.
        o.enableClassStaticizer = false;
      };

  @Parameterized.Parameters(name = "Backend: {0} target: {1}")
  public static Collection<Object[]> data() {
    ImmutableList.Builder<Object[]> builder = new ImmutableList.Builder<>();
    for (Backend backend : Backend.values()) {
      for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
        builder.add(new Object[]{backend, targetVersion});
      }
    }
    return builder.build();
  }

  public MetadataStripTest(Backend backend, KotlinTargetVersion targetVersion) {
    this.backend = backend;
    this.targetVersion = targetVersion;
  }

  @Test
  public void testJstyleRunnable() throws Exception {
    final String folder = "lambdas_jstyle_runnable";
    final String mainClassName = "lambdas_jstyle_runnable.MainKt";
    final String implementer1ClassName = "lambdas_jstyle_runnable.Implementer1Kt";
    TestRunResult result = testForR8(backend)
        .addProgramFiles(getKotlinJarFile(folder))
        .addProgramFiles(getJavaJarFile(folder))
        .addProgramFiles(ToolHelper.getKotlinReflectJar())
        .enableProguardTestOptions()
        .enableInliningAnnotations()
        .addKeepMainRule(mainClassName)
        .addKeepRules(KEEP_ANNOTATIONS)
        .addKeepRules("-keep class kotlin.Metadata")
        .addOptionsModification(optionsModifier)
        .run(mainClassName);
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

  private Path getKotlinJarFile(String folder) {
    return Paths.get(ToolHelper.TESTS_BUILD_DIR, "kotlinR8TestResources",
        targetVersion.getFolderName(), folder + FileUtils.JAR_EXTENSION);
  }

  private Path getJavaJarFile(String folder) {
    return Paths.get(ToolHelper.TESTS_BUILD_DIR, "kotlinR8TestResources",
        targetVersion.getFolderName(), folder + ".java" + FileUtils.JAR_EXTENSION);
  }
}
