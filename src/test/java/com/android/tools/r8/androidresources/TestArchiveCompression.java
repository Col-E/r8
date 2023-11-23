// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.ZipUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TestArchiveCompression extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withDefaultDexRuntime().withAllApiLevels().build();
  }

  public static AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(R.string.class, R.drawable.class)
        .build(temp);
  }

  @Test
  public void testR8() throws Exception {
    AndroidTestResource testResources = getTestResources(temp);
    Path resourceZip = testResources.getResourceZip();
    assertTrue(ZipUtils.containsEntry(resourceZip, "res/drawable/foobar.png"));
    assertTrue(ZipUtils.containsEntry(resourceZip, "res/drawable/unused_drawable.png"));
    assertTrue(ZipUtils.containsEntry(resourceZip, "AndroidManifest.xml"));
    assertTrue(ZipUtils.containsEntry(resourceZip, "resources.pb"));

    validateCompression(resourceZip);
    Path resourceOutput = temp.newFile("resources_out.zip").toPath();
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(FooBar.class)
        .addAndroidResources(testResources, resourceOutput)
        .addKeepMainRule(FooBar.class)
        .compile()
        .run(parameters.getRuntime(), FooBar.class)
        .assertSuccess();
    assertTrue(ZipUtils.containsEntry(resourceOutput, "res/drawable/foobar.png"));
    assertFalse(ZipUtils.containsEntry(resourceOutput, "res/drawable/unused_drawable.png"));
    assertTrue(ZipUtils.containsEntry(resourceOutput, "AndroidManifest.xml"));
    assertTrue(ZipUtils.containsEntry(resourceOutput, "resources.pb"));
    validateCompression(resourceOutput);
  }

  private static void validateCompression(Path resourceZip) throws IOException {
    ZipUtils.iter(
        resourceZip,
        entry -> {
          if (entry.getName().endsWith(".png")) {
            assertEquals(ZipEntry.STORED, entry.getMethod());
          } else {
            assertEquals(ZipEntry.DEFLATED, entry.getMethod());
          }
        });
  }

  public static class FooBar {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(R.drawable.foobar);
        System.out.println(R.string.bar);
        System.out.println(R.string.foo);
      }
    }
  }

  public static class R {

    public static class string {

      public static int bar;
      public static int foo;
      public static int unused_string;
    }

    public static class drawable {

      public static int foobar;
      public static int unused_drawable;
    }
  }
}
