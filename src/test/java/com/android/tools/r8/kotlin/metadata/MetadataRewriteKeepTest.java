// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.kotlin.KotlinMetadataAnnotationWrapper;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.util.Collection;
import kotlinx.metadata.jvm.KotlinClassMetadata;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteKeepTest extends KotlinMetadataTestBase {

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  private final TestParameters parameters;

  public MetadataRewriteKeepTest(TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
        .setMinApi(parameters.getApiLevel())
        .addKeepKotlinMetadata()
        .addKeepRules("-keep class kotlin.io.** { *; }")
        .allowDiagnosticWarningMessages()
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .inspect(this::inspect);
  }

  @Test
  public void testR8KeepIf() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
        .setMinApi(parameters.getApiLevel())
        .addKeepRules("-keep class kotlin.io.** { *; }")
        .addKeepRules("-if class *", "-keep class kotlin.Metadata { *; }")
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .allowDiagnosticWarningMessages()
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    // All kept classes should have their kotlin metadata.
    for (FoundClassSubject clazz : inspector.allClasses()) {
      if (clazz.getFinalName().startsWith("kotlin.io")
          || clazz.getFinalName().equals("kotlin.Metadata")
          || clazz.getFinalName().equals("kotlin.jvm.JvmName")) {
        KotlinClassMetadata kotlinClassMetadata = clazz.getKotlinClassMetadata();
        assertNotNull(kotlinClassMetadata);
        assertNotNull(KotlinMetadataAnnotationWrapper.wrap(kotlinClassMetadata).data2());
      } else {
        assertNull(clazz.getKotlinClassMetadata());
      }
    }
  }
}
