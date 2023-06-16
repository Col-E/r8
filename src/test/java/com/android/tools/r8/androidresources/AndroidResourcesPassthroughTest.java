// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ArchiveProtoAndroidResourceConsumer;
import com.android.tools.r8.ArchiveProtoAndroidResourceProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
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
    String xmlPath = "res/xml/bar.xml";
    Path resources =
        ZipBuilder.builder(temp.newFile("resources.zip").toPath())
            .addText(manifestPath, AndroidResourceTestingUtils.TEST_MANIFEST)
            .addBytes(resourcePath, AndroidResourceTestingUtils.TEST_RESOURCE_TABLE)
            .addBytes(pngPath, AndroidResourceTestingUtils.TINY_PNG)
            .addBytes(xmlPath, AndroidResourceTestingUtils.TINY_PROTO_XML)
            .build();
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
    assertTrue(
        Arrays.equals(
            ZipUtils.readSingleEntry(output, manifestPath),
            AndroidResourceTestingUtils.TEST_MANIFEST.getBytes(StandardCharsets.UTF_8)));
    assertTrue(
        Arrays.equals(
            ZipUtils.readSingleEntry(output, resourcePath),
            AndroidResourceTestingUtils.TEST_RESOURCE_TABLE));
    assertTrue(
        Arrays.equals(
            ZipUtils.readSingleEntry(output, pngPath), AndroidResourceTestingUtils.TINY_PNG));
    assertTrue(
        Arrays.equals(
            ZipUtils.readSingleEntry(output, xmlPath), AndroidResourceTestingUtils.TINY_PROTO_XML));
  }

  public static class FooBar {

    public static void main(String[] args) {
      System.out.println("Hello World");
    }
  }
}
