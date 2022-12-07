// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.varhandle;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.examples.jdk9.VarHandle;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ZipUtils;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class VarHandleDesugaringTestBase extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        // Running on 4.0.4 and 4.4.4 needs to be checked. Output seems correct, but at the
        // same time there are VFY errors on stderr.
        .withDexRuntimesStartingFromExcluding(Version.V4_4_4)
        .withAllApiLevels()
        .build();
  }

  protected abstract String getMainClass();

  protected String getKeepRules() {
    return "";
  }

  protected abstract List<String> getJarEntries();

  protected abstract String getExpectedOutputForReferenceImplementation();

  protected String getExpectedOutputForArtImplementation() {
    return getExpectedOutputForReferenceImplementation();
  }

  // TODO(b/247076137): Remove this when all tests can run with desugaring.
  protected boolean getTestWithDesugaring() {
    return false;
  }

  @Test
  public void testReference() throws Throwable {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramFiles(VarHandle.jar())
        .run(parameters.getRuntime(), getMainClass())
        .assertSuccessWithOutput(getExpectedOutputForReferenceImplementation());
  }

  private List<byte[]> getProgramClassFileData() {
    return getJarEntries().stream()
        .map(
            entry -> {
              try {
                return ZipUtils.readSingleEntry(VarHandle.jar(), entry);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            })
        .collect(Collectors.toList());
  }

  // TODO(b/247076137: Also turn on VarHandle desugaring for R8 tests.
  @Test
  public void testD8() throws Throwable {
    assumeTrue(parameters.isDexRuntime());
    if (getTestWithDesugaring()) {
      testForD8(parameters.getBackend())
          .addProgramClassFileData(getProgramClassFileData())
          .setMinApi(parameters.getApiLevel())
          .addOptionsModification(options -> options.enableVarHandleDesugaring = true)
          .run(parameters.getRuntime(), getMainClass())
          .applyIf(
              parameters.isDexRuntime()
                  && parameters.asDexRuntime().getVersion().isOlderThanOrEqual(Version.V4_4_4),
              // TODO(b/247076137): Running on 4.0.4 and 4.4.4 needs to be checked. Output seems
              // correct, but at the same time there are VFY errors on stderr.
              r -> r.assertFailureWithErrorThatThrows(NoSuchFieldException.class),
              r ->
                  r.assertSuccessWithOutput(
                      parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.T)
                              && parameters
                                  .getDexRuntimeVersion()
                                  .isNewerThanOrEqual(Version.V13_0_0)
                          ? getExpectedOutputForArtImplementation()
                          : getExpectedOutputForReferenceImplementation()));
    } else {
      testForD8(parameters.getBackend())
          .addProgramClassFileData(getProgramClassFileData())
          .setMinApi(parameters.getApiLevel())
          .run(parameters.getRuntime(), getMainClass())
          .applyIf(
              // VarHandle is available from Android 9, even though it was not a public API until
              // 13.
              parameters.asDexRuntime().getVersion().isOlderThanOrEqual(Version.V7_0_0),
              r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class),
              parameters.getApiLevel().isLessThan(AndroidApiLevel.P)
                  || parameters.asDexRuntime().getVersion().isOlderThanOrEqual(Version.V8_1_0),
              r -> r.assertFailure(),
              r -> r.assertSuccessWithOutput(getExpectedOutputForArtImplementation()));
    }
  }

  // TODO(b/247076137: Also turn on VarHandle desugaring for R8 tests.
  @Test
  public void testR8() throws Throwable {
    testForR8(parameters.getBackend())
        .applyIf(
            parameters.isDexRuntime(),
            // Use android.jar from Android T to get the VarHandle type.
            b -> b.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T)),
            // Use system JDK to have references types including StringConcatFactory.
            b -> b.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
        .addProgramClassFileData(getProgramClassFileData())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(getMainClass())
        .addKeepRules(getKeepRules())
        .applyIf(
            parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(AndroidApiLevel.O),
            R8TestBuilder::allowDiagnosticWarningMessages)
        .run(parameters.getRuntime(), getMainClass())
        .applyIf(
            // VarHandle is available from Android 9, even though it was not a public API until 13.
            parameters.isDexRuntime()
                && parameters.asDexRuntime().getVersion().isOlderThanOrEqual(Version.V7_0_0),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class),
            parameters.isDexRuntime()
                && (parameters.getApiLevel().isLessThan(AndroidApiLevel.P)
                    || parameters.asDexRuntime().getVersion().isOlderThanOrEqual(Version.V8_1_0)),
            r -> r.assertFailure(),
            r ->
                r.assertSuccessWithOutput(
                    parameters.isCfRuntime()
                        ? getExpectedOutputForReferenceImplementation()
                        : getExpectedOutputForArtImplementation()));
  }
}
