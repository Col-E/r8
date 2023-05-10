// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi;

import static com.android.tools.r8.ToolHelper.R8LIB_JAR;
import static com.android.tools.r8.ToolHelper.R8_JAR;
import static com.android.tools.r8.ToolHelper.isTestingR8Lib;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.compilerapi.androidplatformbuild.AndroidPlatformBuildApiTest;
import com.android.tools.r8.compilerapi.artprofiles.ArtProfilesForRewritingApiTest;
import com.android.tools.r8.compilerapi.assertionconfiguration.AssertionConfigurationTest;
import com.android.tools.r8.compilerapi.cancelcompilationchecker.CancelCompilationCheckerTest;
import com.android.tools.r8.compilerapi.classconflictresolver.ClassConflictResolverTest;
import com.android.tools.r8.compilerapi.desugardependencies.DesugarDependenciesTest;
import com.android.tools.r8.compilerapi.diagnostics.ProguardKeepRuleDiagnosticsApiTest;
import com.android.tools.r8.compilerapi.diagnostics.UnsupportedFeaturesDiagnosticApiTest;
import com.android.tools.r8.compilerapi.extractmarker.ExtractMarkerApiTest;
import com.android.tools.r8.compilerapi.globalsynthetics.GlobalSyntheticsTest;
import com.android.tools.r8.compilerapi.inputdependencies.InputDependenciesTest;
import com.android.tools.r8.compilerapi.mapid.CustomMapIdTest;
import com.android.tools.r8.compilerapi.mockdata.MockClass;
import com.android.tools.r8.compilerapi.mockdata.MockClassWithAssertion;
import com.android.tools.r8.compilerapi.mockdata.PostStartupMockClass;
import com.android.tools.r8.compilerapi.partitionmap.PartitionMapCommandTest;
import com.android.tools.r8.compilerapi.sourcefile.CustomSourceFileTest;
import com.android.tools.r8.compilerapi.startupprofile.StartupProfileApiTest;
import com.android.tools.r8.compilerapi.syntheticscontexts.SyntheticContextsConsumerTest;
import com.android.tools.r8.compilerapi.testsetup.ApiTestingSetUpTest;
import com.android.tools.r8.compilerapi.wrappers.CommandLineParserTest;
import com.android.tools.r8.compilerapi.wrappers.EnableMissingLibraryApiModelingTest;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.rules.TemporaryFolder;

/** Collection of API tests for the D8/R8 compilers. */
public class CompilerApiTestCollection extends BinaryCompatibilityTestCollection<CompilerApiTest> {

  private static final String DIRNAME = "compiler_api_tests";
  private static final Path BINARY_COMPATIBILITY_JAR =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "binary_compatibility_tests", DIRNAME, "tests.jar");

  private static final List<Class<? extends CompilerApiTest>> CLASSES_FOR_BINARY_COMPATIBILITY =
      ImmutableList.of(
          ApiTestingSetUpTest.ApiTest.class,
          CustomMapIdTest.ApiTest.class,
          CustomSourceFileTest.ApiTest.class,
          AssertionConfigurationTest.ApiTest.class,
          InputDependenciesTest.ApiTest.class,
          DesugarDependenciesTest.ApiTest.class,
          GlobalSyntheticsTest.ApiTest.class,
          CommandLineParserTest.ApiTest.class,
          EnableMissingLibraryApiModelingTest.ApiTest.class,
          AndroidPlatformBuildApiTest.ApiTest.class,
          UnsupportedFeaturesDiagnosticApiTest.ApiTest.class,
          ArtProfilesForRewritingApiTest.ApiTest.class,
          StartupProfileApiTest.ApiTest.class,
          ClassConflictResolverTest.ApiTest.class,
          ProguardKeepRuleDiagnosticsApiTest.ApiTest.class,
          SyntheticContextsConsumerTest.ApiTest.class,
          ExtractMarkerApiTest.ApiTest.class,
          PartitionMapCommandTest.ApiTest.class,
          CancelCompilationCheckerTest.ApiTest.class);

  private static final List<Class<? extends CompilerApiTest>> CLASSES_PENDING_BINARY_COMPATIBILITY =
      ImmutableList.of();

  private final TemporaryFolder temp;

  public CompilerApiTestCollection(TemporaryFolder temp) {
    this.temp = temp;
  }

  @Override
  public TemporaryFolder getTemp() {
    return temp;
  }

  @Override
  public List<Class<? extends CompilerApiTest>> getCheckedInTestClasses() {
    return CLASSES_FOR_BINARY_COMPATIBILITY;
  }

  @Override
  public List<Class<? extends CompilerApiTest>> getPendingTestClasses() {
    return CLASSES_PENDING_BINARY_COMPATIBILITY;
  }

  @Override
  public List<Class<?>> getAdditionalClassesForTests() {
    return ImmutableList.of(
        CompilerApiTest.class,
        MockClass.class,
        MockClassWithAssertion.class,
        PostStartupMockClass.class);
  }

  @Override
  public List<Class<?>> getPendingAdditionalClassesForTests() {
    return ImmutableList.of();
  }

  @Override
  public Path getCheckedInTestJar() {
    return BINARY_COMPATIBILITY_JAR;
  }

  // The API tests always link against the jar that the test runner is using.
  @Override
  public Path getTargetJar() {
    return isTestingR8Lib() ? R8LIB_JAR : R8_JAR;
  }

  // Some tests expectations can depend on the lib/nonlib and internal/external behavior.
  // This sets up envvars so the test can determine its running context.
  // This is only called for external invocations.
  public List<String> getVmArgs() {
    return ImmutableList.of(
        makeProperty("com.android.tools.r8.enableTestAssertions", "1"),
        makeProperty(CompilerApiTest.API_TEST_MODE_KEY, CompilerApiTest.API_TEST_MODE_EXTERNAL),
        makeProperty(
            CompilerApiTest.API_TEST_LIB_KEY,
            isTestingR8Lib() ? CompilerApiTest.API_TEST_LIB_YES : CompilerApiTest.API_TEST_LIB_NO));
  }
}
