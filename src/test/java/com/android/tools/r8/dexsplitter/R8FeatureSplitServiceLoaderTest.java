// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexsplitter;

import static com.android.tools.r8.rewrite.ServiceLoaderRewritingTest.getServiceLoaderLoads;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ServiceLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8FeatureSplitServiceLoaderTest extends SplitterTestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public R8FeatureSplitServiceLoaderTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8AllServiceConfigurationInBaseAndNoTypesInFeatures() throws Exception {
    Path base = temp.newFile("base.zip").toPath();
    Path feature1Path = temp.newFile("feature1.zip").toPath();
    testForR8(parameters.getBackend())
        .addProgramClasses(Base.class, I.class, Feature1I.class, Feature2I.class)
        .setMinApi(parameters)
        .addKeepMainRule(Base.class)
        .addFeatureSplit(
            builder -> simpleSplitProvider(builder, feature1Path, temp, Feature3Dummy.class))
        .addDataEntryResources(
            DataEntryResource.fromBytes(
                StringUtils.lines(Feature1I.class.getTypeName(), Feature2I.class.getTypeName())
                    .getBytes(),
                "META-INF/services/" + I.class.getTypeName(),
                Origin.unknown()))
        .compile()
        .inspect(
            inspector -> {
              assertEquals(0, getServiceLoaderLoads(inspector, Base.class));
            })
        .writeToZip(base)
        .run(parameters.getRuntime(), Base.class)
        .assertSuccessWithOutputLines("Feature1I.foo()", "Feature2I.foo()");
  }

  @Test
  public void testR8AllServiceConfigurationInBase() throws Exception {
    Path base = temp.newFile("base.zip").toPath();
    Path feature1Path = temp.newFile("feature1.zip").toPath();
    Path feature2Path = temp.newFile("feature2.zip").toPath();
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(Base.class, I.class)
            .setMinApi(parameters)
            .addKeepMainRule(Base.class)
            .addFeatureSplit(
                builder -> simpleSplitProvider(builder, feature1Path, temp, Feature1I.class))
            .addFeatureSplit(
                builder -> simpleSplitProvider(builder, feature2Path, temp, Feature2I.class))
            .addDataEntryResources(
                DataEntryResource.fromBytes(
                    StringUtils.lines(Feature1I.class.getTypeName(), Feature2I.class.getTypeName())
                        .getBytes(),
                    "META-INF/services/" + I.class.getTypeName(),
                    Origin.unknown()))
            .compile()
            .inspect(
                inspector -> {
                  assertEquals(1, getServiceLoaderLoads(inspector, Base.class));
                })
            .writeToZip(base)
            .addRunClasspathFiles(feature1Path, feature2Path)
            .run(parameters.getRuntime(), Base.class);
    // TODO(b/160888348): This is failing on 7.0
    if (parameters.getRuntime().isDex()
        && parameters.getRuntime().asDex().getVm().getVersion() == Version.V7_0_0) {
      runResult.assertFailureWithErrorThatMatches(containsString("ServiceConfigurationError"));
    } else {
      runResult.assertSuccessWithOutputLines("Feature1I.foo()", "Feature2I.foo()");
    }
  }

  @Test
  public void testR8AllLoaded() throws Exception {
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(Base.class, I.class)
            .setMinApi(parameters)
            .addKeepMainRule(Base.class)
            .addFeatureSplitWithResources(
                ImmutableList.of(
                    new Pair<>(
                        "META-INF/services/" + I.class.getTypeName(),
                        StringUtils.lines(Feature1I.class.getTypeName()))),
                Feature1I.class)
            .addFeatureSplitWithResources(
                ImmutableList.of(
                    new Pair<>(
                        "META-INF/services/" + I.class.getTypeName(),
                        StringUtils.lines(Feature2I.class.getTypeName()))),
                Feature2I.class)
            .compile()
            .inspect(
                baseInspector -> assertEquals(1, getServiceLoaderLoads(baseInspector, Base.class)),
                feature1Inspector ->
                    assertThat(feature1Inspector.clazz(Feature1I.class), isPresent()),
                feature2Inspector ->
                    assertThat(feature2Inspector.clazz(Feature2I.class), isPresent()))
            .addFeatureSplitsToRunClasspathFiles()
            .run(parameters.getRuntime(), Base.class);
    // TODO(b/160888348): This is failing on 7.0
    if (parameters.getRuntime().isDex()
        && parameters.getRuntime().asDex().getVm().getVersion() == Version.V7_0_0) {
      runResult.assertFailureWithErrorThatMatches(containsString("ServiceConfigurationError"));
    } else {
      runResult.assertSuccessWithOutputLines("Feature1I.foo()", "Feature2I.foo()");
    }
  }

  @Test
  public void testR8WithServiceFileInSeparateFeature() throws Exception {
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(Base.class, I.class)
            .setMinApi(parameters)
            .addKeepMainRule(Base.class)
            .addFeatureSplit(Feature1I.class)
            .addFeatureSplit(Feature2I.class)
            .addFeatureSplitWithResources(
                ImmutableList.of(
                    new Pair<>(
                        "META-INF/services/" + I.class.getTypeName(),
                        StringUtils.lines(
                            Feature1I.class.getTypeName(), Feature2I.class.getTypeName()))),
                Feature3Dummy.class)
            .compile()
            .inspect(
                baseInspector -> assertEquals(1, getServiceLoaderLoads(baseInspector, Base.class)),
                feature1Inspector ->
                    assertThat(feature1Inspector.clazz(Feature1I.class), isPresent()),
                feature2Inspector ->
                    assertThat(feature2Inspector.clazz(Feature2I.class), isPresent()),
                feature3Inspector -> assertTrue(feature3Inspector.allClasses().isEmpty()))
            .addFeatureSplitsToRunClasspathFiles()
            .run(parameters.getRuntime(), Base.class);
    // TODO(b/160888348): This is failing on 7.0
    if (parameters.getRuntime().isDex()
        && parameters.getRuntime().asDex().getVm().getVersion() == Version.V7_0_0) {
      runResult.assertFailureWithErrorThatMatches(containsString("ServiceConfigurationError"));
    } else {
      runResult.assertSuccessWithOutputLines("Feature1I.foo()", "Feature2I.foo()");
    }
  }

  @Test
  public void testR8OnlyFeature2() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Base.class, I.class)
        .setMinApi(parameters)
        .addKeepMainRule(Base.class)
        .addFeatureSplitWithResources(
            ImmutableList.of(
                new Pair<>(
                    "META-INF/services/" + I.class.getTypeName(),
                    StringUtils.lines(Feature1I.class.getTypeName()))),
            Feature1I.class)
        .addFeatureSplitWithResources(
            ImmutableList.of(
                new Pair<>(
                    "META-INF/services/" + I.class.getTypeName(),
                    StringUtils.lines(Feature2I.class.getTypeName()))),
            Feature2I.class)
        .compile()
        .inspect(
            baseInspector -> assertEquals(1, getServiceLoaderLoads(baseInspector, Base.class)),
            feature1Inspector -> assertThat(feature1Inspector.clazz(Feature1I.class), isPresent()),
            feature2Inspector -> assertThat(feature2Inspector.clazz(Feature2I.class), isPresent()))
        .apply(compileResult -> compileResult.addRunClasspathFiles(compileResult.getFeature(1)))
        .run(parameters.getRuntime(), Base.class)
        // TODO(b/160889305): This should work.
        .assertFailureWithErrorThatMatches(containsString("java.lang.ClassNotFoundException"));
  }

  public interface I {
    void foo();
  }

  public static class Base {

    public static void main(String[] args) {
      for (I i : ServiceLoader.load(I.class, null)) {
        i.foo();
      }
    }
  }

  public static class Feature1I implements I {

    @Override
    public void foo() {
      System.out.println("Feature1I.foo()");
    }
  }

  public static class Feature2I implements I {

    @Override
    public void foo() {
      System.out.println("Feature2I.foo()");
    }
  }

  public static class Feature3Dummy {}
}
