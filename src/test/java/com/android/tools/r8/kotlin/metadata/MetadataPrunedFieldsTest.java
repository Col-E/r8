// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.kotlin.metadata.metadata_pruned_fields.Main;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** This is a reproduction of b/161230424. */
@RunWith(Parameterized.class)
public class MetadataPrunedFieldsTest extends KotlinMetadataTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), KotlinTargetVersion.values());
  }

  public MetadataPrunedFieldsTest(TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static Map<KotlinTargetVersion, Path> libJars = new HashMap<>();

  @BeforeClass
  public static void createLibJar() throws Exception {
    String baseLibFolder = PKG_PREFIX + "/metadata_pruned_fields";
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path baseLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(getKotlinFileInTest(baseLibFolder, "Methods"))
              .compile();
      libJars.put(targetVersion, baseLibJar);
    }
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .addProgramFiles(libJars.get(targetVersion))
        .addProgramClassFileData(Main.dump())
        .addKeepRules("-keep class " + PKG + ".metadata_pruned_fields.MethodsKt { *; }")
        .addKeepRules("-keep class kotlin.Metadata { *** pn(); }")
        .addKeepMainRule(Main.class)
        .allowDiagnosticWarningMessages()
        .setMinApi(parameters.getApiLevel())
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .compile()
        .inspect(
            codeInspector -> {
              final ClassSubject clazz = codeInspector.clazz("kotlin.Metadata");
              assertThat(clazz, isPresent());
              assertThat(clazz.uniqueMethodWithName("pn"), isPresent());
              assertThat(clazz.uniqueMethodWithName("d1"), not(isPresent()));
              assertThat(clazz.uniqueMethodWithName("d2"), not(isPresent()));
              assertThat(clazz.uniqueMethodWithName("bv"), not(isPresent()));
            })
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("", "Hello World!");
  }
}
