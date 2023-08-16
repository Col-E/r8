// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.ArchiveProtoAndroidResourceConsumer;
import com.android.tools.r8.ArchiveProtoAndroidResourceProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ZipUtils;
import java.nio.charset.Charset;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AndroidResourcesPassthroughTest extends TestBase {

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
            .withSimpleManifest()
            .addStringValue("app_name", "The App")
            .addDrawable("foo.png", AndroidResourceTestingUtils.TINY_PNG)
            .build(temp);
    Path resources = testResource.getResourceZip();
    Path output = temp.newFile("resources_out.zip").toPath();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addOptionsModification(
            o -> {
              o.androidResourceProvider =
                  new ArchiveProtoAndroidResourceProvider(resources, new PathOrigin(resources));
              o.androidResourceConsumer = new ArchiveProtoAndroidResourceConsumer(output);
            })
        .addKeepMainRule(FooBar.class)
        .run(parameters.getRuntime(), FooBar.class)
        .assertSuccessWithOutputLines("Hello World");
    assertArrayEquals(
        ZipUtils.readSingleEntry(output, manifestPath),
        ZipUtils.readSingleEntry(resources, manifestPath));
    assertArrayEquals(
        ZipUtils.readSingleEntry(output, resourcePath),
        ZipUtils.readSingleEntry(resources, resourcePath));
    assertArrayEquals(
        ZipUtils.readSingleEntry(output, pngPath), ZipUtils.readSingleEntry(resources, pngPath));
    String rClassContent =
        FileUtils.readTextFile(
            testResource.getRClass().getJavaFilePath(), Charset.defaultCharset());
    assertThat(rClassContent, containsString("app_name"));
    assertThat(rClassContent, containsString("foo"));
  }

  public static class FooBar {

    public static void main(String[] args) {
      System.out.println("Hello World");
    }
  }
}
