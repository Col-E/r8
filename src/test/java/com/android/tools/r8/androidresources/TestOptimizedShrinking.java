// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import com.android.tools.r8.ResourceShrinkerConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.androidresources.TestOptimizedShrinking.R.styleable;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TestOptimizedShrinking extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean debug;

  @Parameter(2)
  public boolean optimized;

  @Parameters(name = "{0}, debug: {1}, optimized: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDefaultDexRuntime().withAllApiLevels().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  public static AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(R.string.class, R.drawable.class, R.styleable.class)
        .build(temp);
  }

  @Test
  public void testR8() throws Exception {
    AndroidTestResource testResources = getTestResources(temp);

    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(FooBar.class)
        .addAndroidResources(testResources)
        .addKeepMainRule(FooBar.class)
        .applyIf(
            optimized,
            builder ->
                builder.addOptionsModification(
                    internalOptions ->
                        internalOptions.resourceShrinkerConfiguration =
                            ResourceShrinkerConfiguration.builder(null)
                                .enableOptimizedShrinkingWithR8()
                                .build()))
        .applyIf(debug, builder -> builder.debug())
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              resourceTableInspector.assertContainsResourceWithName("string", "bar");
              resourceTableInspector.assertContainsResourceWithName("string", "foo");
              resourceTableInspector.assertContainsResourceWithName("drawable", "foobar");
              // In debug mode legacy shrinker will not attribute our $R inner class as an R class
              // (this is only used for testing, _real_ R classes are not inner classes.
              if (!debug || optimized) {
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "string", "unused_string");
                resourceTableInspector.assertDoesNotContainResourceWithName(
                    "drawable", "unused_drawable");
              }
              resourceTableInspector.assertContainsResourceWithName("styleable", "our_styleable");
              // The resource shrinker is currently keeping all styleables,
              // so we don't remove this even when it is unused.
              resourceTableInspector.assertContainsResourceWithName(
                  "styleable", "unused_styleable");
              // We do remove the attributes pointed at by the unreachable styleable.
              for (int i = 0; i < 4; i++) {
                resourceTableInspector.assertContainsResourceWithName(
                    "attr", "attr_our_styleable" + i);
                if (!debug || optimized) {
                  resourceTableInspector.assertDoesNotContainResourceWithName(
                      "attr", "attr_unused_styleable" + i);
                }
              }
            })
        .run(parameters.getRuntime(), FooBar.class)
        .assertSuccess();
  }

  public static class FooBar {

    public static void main(String[] args) {
      if (System.currentTimeMillis() == 0) {
        System.out.println(R.drawable.foobar);
        System.out.println(R.string.bar);
        System.out.println(R.string.foo);
        System.out.println(styleable.our_styleable);
      }
    }
  }

  public static class R {

    public static class string {
      public static int bar;
      public static int foo;
      public static int unused_string;
    }

    public static class styleable {
      public static int[] our_styleable;
      public static int[] unused_styleable;
    }

    public static class drawable {

      public static int foobar;
      public static int unused_drawable;
    }
  }
}
