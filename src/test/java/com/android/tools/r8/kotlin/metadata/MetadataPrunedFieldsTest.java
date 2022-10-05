// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.kotlin.metadata.metadata_pruned_fields.Main;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** This is a reproduction of b/161230424. */
@RunWith(Parameterized.class)
public class MetadataPrunedFieldsTest extends KotlinMetadataTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public MetadataPrunedFieldsTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer libJars =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/metadata_pruned_fields", "Methods"));

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(kotlinc.getKotlinStdlibJar())
        .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
        .addProgramClassFileData(Main.dump())
        .addKeepRules("-keep class " + PKG + ".metadata_pruned_fields.MethodsKt { *; }")
        .addKeepRules("-keep class kotlin.Metadata { *** pn(); }")
        .addKeepMainRule(Main.class)
        .allowDiagnosticWarningMessages()
        .setMinApi(parameters.getApiLevel())
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .addOptionsModification(
            internalOptions -> {
              // When checking for metadata being equal if not rewritten, we parse the original data
              // again. However, for this particular test, a field of the metadata has been removed
              // and we cannot parse the metadata again.
              internalOptions.testing.keepMetadataInR8IfNotRewritten = false;
            })
        .compile()
        .inspect(
            codeInspector -> {
              final ClassSubject clazz = codeInspector.clazz("kotlin.Metadata");
              assertThat(clazz, isPresent());
              assertThat(clazz.uniqueMethodWithOriginalName("pn"), isPresent());
              assertThat(clazz.uniqueMethodWithOriginalName("d1"), not(isPresent()));
              assertThat(clazz.uniqueMethodWithOriginalName("d2"), not(isPresent()));
              assertThat(clazz.uniqueMethodWithOriginalName("bv"), not(isPresent()));
            })
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("", "Hello World!");
  }
}
