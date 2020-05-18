// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteKeepTest extends KotlinMetadataTestBase {

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  private final TestParameters parameters;

  public MetadataRewriteKeepTest(TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .setMinApi(parameters.getApiLevel())
        .addKeepKotlinMetadata()
        .addKeepRules("-keep class kotlin.io.** { *; }")
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .compile()
        .inspect(this::inspect);
  }

  @Test
  public void testR8KeepPartial() throws Exception {
    // This test is a bit weird, since it shows that we can remove params from the kotlin.Metadata
    // class, but still be able to fully read the kotlin.Metadata.
    testForR8(parameters.getBackend())
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .setMinApi(parameters.getApiLevel())
        .addKeepRules("-keep class kotlin.Metadata { *** d1(); }")
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .compile()
        .inspect(
            inspector -> {
              inspect(inspector);
              ClassSubject kotlinMetadataClass = inspector.clazz("kotlin.Metadata");
              assertThat(kotlinMetadataClass, isPresent());
              assertEquals(1, kotlinMetadataClass.allMethods().size());
              assertNotNull(kotlinMetadataClass.getKmClass().getName());
            });
  }

  @Test
  public void testR8KeepPartialCooking() throws Exception {
    // This test is a bit weird, since it shows that we can remove params from the kotlin.Metadata
    // class, but still be able to fully read the kotlin.Metadata externally.
    testForR8(parameters.getBackend())
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .setMinApi(parameters.getApiLevel())
        .addKeepRules("-keep class kotlin.Metadata { *** d1(); }")
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .compile()
        .inspect(
            inspector -> {
              inspect(inspector);
              ClassSubject kotlinMetadataClass = inspector.clazz("kotlin.Metadata");
              assertThat(kotlinMetadataClass, isPresent());
              assertEquals(1, kotlinMetadataClass.allMethods().size());
              assertNotNull(kotlinMetadataClass.getKmClass().getName());
            });
  }

  @Test
  public void testR8KeepIf() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .setMinApi(parameters.getApiLevel())
        .addKeepRules("-keep class kotlin.io.** { *; }")
        .addKeepRules("-if class * { *** $VALUES; }", "-keep class kotlin.Metadata { *; }")
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .compile()
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    // All kept classes should have their kotlin metadata.
    for (FoundClassSubject clazz : inspector.allClasses()) {
      if (clazz.getFinalName().startsWith("kotlin.io")
          || clazz.getFinalName().equals("kotlin.Metadata")) {
        assertNotNull(clazz.getKotlinClassMetadata());
        assertNotNull(clazz.getKotlinClassMetadata().getHeader().getData2());
      } else {
        assertNull(clazz.getKotlinClassMetadata());
      }
    }
  }
}
