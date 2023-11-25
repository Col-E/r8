// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticException;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.androidresources.ResourceShrinkingWithFeatures.FeatureSplit.FeatureSplitMain;
import java.io.IOException;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ResourceShrinkingWithFeatures extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public static AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(R.string.class)
        .build(temp);
  }

  public static AndroidTestResource getFeatureSplitTestResources(TemporaryFolder temp)
      throws IOException {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .setPackageId(0x7E)
        .addRClassInitializeWithDefaultValues(FeatureSplit.R.string.class)
        .build(temp);
  }

  @Test
  public void testFailureIfNotResourcesOrCode() throws Exception {
    try {
      testForR8(parameters.getBackend())
          .setMinApi(parameters)
          .addProgramClasses(Base.class)
          .addFeatureSplit(builder -> builder.build())
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                diagnostics.assertErrorThatMatches(diagnosticException(AssertionError.class));
              });
    } catch (CompilationFailedException e) {
      return;
    }
    fail();
  }

  @Test
  public void testR8ReferenceFeatureResourcesFromFeature() throws Exception {
    // We reference a feature resource from a feature.
    testR8(false);
  }

  @Test
  public void testR8ReferenceFeatureResourcesFromBase() throws Exception {
    // We reference a feature resource from the base.
    testR8(true);
  }

  private void testR8(boolean referenceFromBase) throws Exception {
    TemporaryFolder featureSplitTemp = ToolHelper.getTemporaryFolderForTest();
    featureSplitTemp.create();
    R8FullTestBuilder r8FullTestBuilder =
        testForR8(parameters.getBackend()).setMinApi(parameters).addProgramClasses(Base.class);
    if (referenceFromBase) {
      r8FullTestBuilder.addProgramClasses(FeatureSplit.FeatureSplitMain.class);
    } else {
      r8FullTestBuilder.addFeatureSplit(FeatureSplit.FeatureSplitMain.class);
    }
    R8TestCompileResult compile =
        r8FullTestBuilder
            .addAndroidResources(getTestResources(temp))
            .addFeatureSplitAndroidResources(
                getFeatureSplitTestResources(featureSplitTemp), FeatureSplit.class.getName())
            .addKeepMainRule(Base.class)
            .addKeepMainRule(FeatureSplitMain.class)
            .compile();
    compile
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("string", "base_used");
              resourceTableInspector.assertDoesNotContainResourceWithName("string", "base_unused");
            })
        .inspectShrunkenResourcesForFeature(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("string", "feature_used");
              resourceTableInspector.assertDoesNotContainResourceWithName(
                  "string", "feature_unused");
            },
            FeatureSplit.class.getName())
        .run(parameters.getRuntime(), Base.class)
        .assertSuccess();
  }

  public static class Base {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(R.string.base_used);
      }
    }
  }

  public static class R {

    public static class string {

      public static int base_used;
      public static int base_unused;
    }
  }

  public static class FeatureSplit {
    public static class FeatureSplitMain {
      public static void main(String[] args) {
        if (System.currentTimeMillis() == 0) {
          System.out.println(R.string.feature_used);
        }
      }
    }

    public static class R {
      public static class string {
        public static int feature_used;
        public static int feature_unused;
      }
    }
  }
}
