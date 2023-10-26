// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.ResourceTableInspector;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ZipUtils;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SimpleNoCodeReferenceAndroidResourceTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    String manifestPath = "AndroidManifest.xml";
    String resourcePath = "resources.pb";
    String pngPath = "res/drawable/foo.png";

    AndroidTestResource testResource =
        new AndroidTestResourceBuilder()
            .withSimpleManifestAndAppNameString()
            .addDrawable("foo.png", AndroidResourceTestingUtils.TINY_PNG)
            .addDrawable("bar.png", AndroidResourceTestingUtils.TINY_PNG)
            .build(temp);
    Path resources = testResource.getResourceZip();
    Path output = temp.newFile("resources_out.zip").toPath();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addAndroidResources(testResource, output)
        .addKeepMainRule(FooBar.class)
        .compile()
        .inspectShrunkenResources(
            shrunkenInspector -> {
              // Reachable from the manifest
              shrunkenInspector.assertContainsResourceWithName("string", "app_name");
              // Not reachable from anything
              shrunkenInspector.assertDoesNotContainResourceWithName("drawable", "foo");
              shrunkenInspector.assertDoesNotContainResourceWithName("drawable", "bar");
              try {
                assertFalse(ZipUtils.containsEntry(output, pngPath));
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })
        .run(parameters.getRuntime(), FooBar.class)
        .assertSuccessWithOutputLines("Hello World");
    // We don't touch the manifest
    assertArrayEquals(
        ZipUtils.readSingleEntry(output, manifestPath),
        ZipUtils.readSingleEntry(resources, manifestPath));

    String rClassContent =
        FileUtils.readTextFile(
            testResource.getRClass().getJavaFilePath(), Charset.defaultCharset());
    assertFalse(
        Arrays.equals(
            ZipUtils.readSingleEntry(output, resourcePath),
            ZipUtils.readSingleEntry(resources, resourcePath)));
    assertThat(rClassContent, containsString("app_name"));
    assertThat(rClassContent, containsString("foo"));
    assertThat(rClassContent, containsString("bar"));
    assertTrue(ZipUtils.containsEntry(resources, pngPath));
    ResourceTableInspector resourceTableInspector =
        new ResourceTableInspector(
            ZipUtils.readSingleEntry(testResource.getResourceZip(), resourcePath));
    resourceTableInspector.assertContainsResourceWithName("string", "app_name");
    resourceTableInspector.assertContainsResourceWithName("drawable", "foo");
    resourceTableInspector.assertContainsResourceWithName("drawable", "bar");
  }

  public static class FooBar {

    public static void main(String[] args) {
      System.out.println("Hello World");
    }
  }
}
