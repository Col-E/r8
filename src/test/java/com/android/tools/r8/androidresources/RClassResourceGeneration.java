// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RClassResourceGeneration extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  private static AndroidTestResource testResource;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @BeforeClass
  public static void setup() throws Exception {
    testResource =
        new AndroidTestResourceBuilder()
            .withSimpleManifestAndAppNameString()
            .addRClassInitializeWithDefaultValues(R.string.class, R.drawable.class)
            .build(getStaticTemp());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(FooBar.class)
        .addAndroidResources(testResource)
        .addKeepMainRule(FooBar.class)
        .run(parameters.getRuntime(), FooBar.class)
        // The values from the aapt2 generated R class (validated in testResourceRewriting below)
        //    drawable:
        //      foobar:  0x7f010000
        //    String:
        //      app_name 0x7f020000
        //      foo      0x7f020001
        //      bar      0x7f020002
        .assertSuccessWithOutputLines("" + 0x7f010000, "" + 0x7f020001, "" + 0x7f020002)
        .inspect(
            codeInspector -> {
              assertEquals(codeInspector.clazz(FooBar.class).allFields().size(), 0);
              assertEquals(codeInspector.clazz(FooBar.class).allMethods().size(), 1);
              assertEquals(codeInspector.allClasses().size(), 1);
            });
  }

  @Test
  public void testResourceRewriting() throws Exception {
    AndroidApp resourceApp =
        AndroidApp.builder()
            .addClassProgramData(testResource.getRClass().getClassFileData())
            .build();
    CodeInspector inspector = new CodeInspector(resourceApp);
    ClassSubject stringClazz = inspector.clazz(R.string.class);
    // Implicitly added with the manifest
    ensureIntFieldWithValue(stringClazz, "app_name");

    ensureIntFieldWithValue(stringClazz, "bar");
    ensureIntFieldWithValue(stringClazz, "foo");
    ensureIntFieldWithValue(inspector.clazz(R.drawable.class), "foobar");
  }

  private void ensureIntFieldWithValue(ClassSubject clazz, String name) {
    assertTrue(clazz.field("int", name).isPresent());
  }

  public static class FooBar {

    public static void main(String[] args) {
      System.out.println(R.drawable.foobar);
      System.out.println(R.string.bar);
      System.out.println(R.string.foo);
    }
  }

  public static class R {
    public static class string {
      public static int bar;
      public static int foo;
    }

    public static class drawable {
      public static int foobar;
    }
  }
}
