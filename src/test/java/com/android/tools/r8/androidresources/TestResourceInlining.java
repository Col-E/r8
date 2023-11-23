// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResource;
import com.android.tools.r8.androidresources.AndroidResourceTestingUtils.AndroidTestResourceBuilder;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TestResourceInlining extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean optimize;

  @Parameter(2)
  public boolean addResourcesSubclass;

  @Parameters(name = "{0}, optimize: {1}, addResourcesSubclass: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDefaultDexRuntime().withAllApiLevels().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  public static AndroidTestResource getTestResources(TemporaryFolder temp) throws Exception {
    return new AndroidTestResourceBuilder()
        .withSimpleManifestAndAppNameString()
        .addRClassInitializeWithDefaultValues(R.string.class)
        .setOverlayableFor("string", "overlayable")
        .addExtraLanguageString("bar")
        .build(temp);
  }

  public static Path getAndroidResourcesClassInJar(TemporaryFolder temp) throws Exception {
    byte[] androidResourcesClass =
        transformer(Resources.class)
            .setClassDescriptor(DexItemFactory.androidResourcesDescriptorString)
            .transform();
    return testForR8(temp, Backend.DEX)
        .addProgramClassFileData(androidResourcesClass)
        .compile()
        .writeToZip();
  }

  public byte[] getResourcesSubclass() throws IOException {
    return transformer(ResourcesSubclass.class)
        .setSuper(DexItemFactory.androidResourcesDescriptorString)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(Resources.class), DexItemFactory.androidResourcesDescriptorString)
        .transform();
  }

  @Test
  public void testR8Optimized() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClassFileData(
            AndroidResourceTestingUtils.transformResourcesReferences(FooBar.class))
        .applyIf(
            addResourcesSubclass,
            builder -> {
              builder.addProgramClassFileData(getResourcesSubclass());
              builder.addKeepClassRules(ResourcesSubclass.class);
            })
        .addAndroidResources(getTestResources(temp))
        .addKeepMainRule(FooBar.class)
        .applyIf(optimize, R8TestBuilder::enableOptimizedShrinking)
        .addRunClasspathFiles(AndroidResourceTestingUtils.resourcesClassAsDex(temp))
        .compile()
        .inspectShrunkenResources(
            resourceTableInspector -> {
              // We should eventually remove this when optimizing
              resourceTableInspector.assertContainsResourceWithName("string", "foo");
              // Has multiple values, don't inline
              resourceTableInspector.assertContainsResourceWithName("string", "bar");
              // Has overlayable value, don't inline
              resourceTableInspector.assertContainsResourceWithName("string", "bar");
              resourceTableInspector.assertDoesNotContainResourceWithName(
                  "string", "unused_string");
            })
        .inspect(
            inspector -> {
              // We should have removed one of the calls to getString if we are optimizing.
              MethodSubject mainMethodSubject = inspector.clazz(FooBar.class).mainMethod();
              assertThat(mainMethodSubject, isPresent());
              assertEquals(
                  mainMethodSubject
                      .streamInstructions()
                      .filter(InstructionSubject::isInvokeVirtual)
                      .filter(
                          i ->
                              i.getMethod()
                                  .holder
                                  .descriptor
                                  .toString()
                                  .equals(DexItemFactory.androidResourcesDescriptorString))
                      .count(),
                  optimize && !addResourcesSubclass ? 2 : 3);
            })
        .run(parameters.getRuntime(), FooBar.class)
        .applyIf(
            optimize && !addResourcesSubclass,
            result -> {
              result.assertSuccessWithOutputLines(
                  "foo", Resources.GET_STRING_VALUE, Resources.GET_STRING_VALUE);
            })
        .applyIf(
            !optimize || addResourcesSubclass,
            result -> {
              result.assertSuccessWithOutputLines(
                  Resources.GET_STRING_VALUE,
                  Resources.GET_STRING_VALUE,
                  Resources.GET_STRING_VALUE);
            });
  }

  public static class FooBar {

    public static void main(String[] args) {
      Resources resources = new Resources();
      // Ensure that we correctly handle the out value propagation
      String s = resources.getString(R.string.foo);
      String t = "X";
      String u = System.currentTimeMillis() > 0 ? s : t;
      System.out.println(u);
      System.out.println(resources.getString(R.string.bar));
      System.out.println(resources.getString(R.string.overlayable));
    }
  }

  public static class ResourcesSubclass extends Resources {

    @Override
    public String getString(int id) {
      System.out.println("foobar");
      return super.getString(id);
    }
  }

  public static class R {

    public static class string {
      public static int foo;
      public static int bar;
      public static int overlayable;
      public static int unused_string;
    }
  }
}
