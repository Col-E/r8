// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.TestRuntime.NoneRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;

// Actual test parameters for a specific configuration. Currently just the runtime configuration.
public class TestParameters {

  private final TestRuntime runtime;
  private final AndroidApiLevel apiLevel;
  private final boolean representativeApiLevelForRuntime;

  public TestParameters(TestRuntime runtime) {
    this(runtime, null);
  }

  public TestParameters(TestRuntime runtime, AndroidApiLevel apiLevel) {
    this(runtime, apiLevel, true);
  }

  public TestParameters(
      TestRuntime runtime, AndroidApiLevel apiLevel, boolean representativeApiLevelForRuntime) {
    assert runtime != null;
    this.runtime = runtime;
    this.apiLevel = apiLevel;
    this.representativeApiLevelForRuntime = representativeApiLevelForRuntime;
  }

  public static TestParametersBuilder builder() {
    return new TestParametersBuilder();
  }

  public static TestParametersCollection justNoneRuntime() {
    return builder().withNoneRuntime().build();
  }

  /**
   * Returns true if the runtime uses resolution to lookup the constructor targeted by a given
   * invoke, so that it is valid to have non-rebound constructor invokes.
   *
   * <p>Example: If value `v` is an uninitialized instanceof type `T`, then calling `T.<init>()`
   * succeeds on ART even if `T.<init>()` does not exists, as ART will resolve the constructor, find
   * `Object.<init>()`, and use this method to initialize `v`. On the JVM and on Dalvik, this is a
   * runtime error.
   */
  public boolean canHaveNonReboundConstructorInvoke() {
    return isDexRuntime() && getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L);
  }

  public boolean canUseDefaultAndStaticInterfaceMethods() {
    assert isCfRuntime() || isDexRuntime();
    assert !isCfRuntime() || apiLevel == null
        : "Use canUseDefaultAndStaticInterfaceMethodsWhenDesugaring when using CF api levels.";
    return isCfRuntime()
        || getApiLevel()
            .isGreaterThanOrEqualTo(TestBase.apiLevelWithDefaultInterfaceMethodsSupport());
  }

  public boolean canUseDefaultAndStaticInterfaceMethodsWhenDesugaring() {
    assert isCfRuntime() || isDexRuntime();
    assert apiLevel != null;
    return getApiLevel()
        .isGreaterThanOrEqualTo(TestBase.apiLevelWithDefaultInterfaceMethodsSupport());
  }

  public boolean canUseNativeDexPC() {
    assert isCfRuntime() || isDexRuntime();
    return isDexRuntime() && getDexRuntimeVersion().isNewerThanOrEqual(DexVm.Version.V8_1_0);
  }

  public boolean canUseNestBasedAccesses() {
    assert isCfRuntime() || isDexRuntime();
    return isCfRuntime() && getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK11);
  }

  public boolean canUseNestBasedAccessesWhenDesugaring() {
    assert isCfRuntime() || isDexRuntime();
    assert apiLevel != null;
    return false;
  }

  public boolean canUseRecords() {
    assert isCfRuntime() || isDexRuntime();
    return isCfRuntime() && asCfRuntime().isNewerThanOrEqual(CfVm.JDK14);
  }

  public boolean canUseRecordsWhenDesugaring() {
    assert isCfRuntime() || isDexRuntime();
    assert apiLevel != null;
    return false;
  }

  public boolean runtimeWithClassValue() {
    assert isCfRuntime() || isDexRuntime();
    return isCfRuntime() || getDexRuntimeVersion().isNewerThanOrEqual(DexVm.Version.V14_0_0);
  }

  // Convenience predicates.
  public boolean isDexRuntime() {
    return runtime.isDex();
  }

  public DexRuntime asDexRuntime() {
    return getRuntime().asDex();
  }

  public boolean isCfRuntime() {
    return runtime.isCf();
  }

  public boolean isCfRuntime(CfVm vm) {
    return runtime.isCf() && runtime.asCf().getVm() == vm;
  }

  public CfRuntime asCfRuntime() {
    return getRuntime().asCf();
  }

  public boolean isDexRuntimeVersion(DexVm.Version vm) {
    return isDexRuntime() && getDexRuntimeVersion().isEqualTo(vm);
  }

  public boolean isDexRuntimeVersionNewerThanOrEqual(DexVm.Version vm) {
    return isDexRuntime() && getDexRuntimeVersion().isNewerThanOrEqual(vm);
  }

  public boolean isDexRuntimeVersionOlderThanOrEqual(DexVm.Version vm) {
    return isDexRuntime() && getDexRuntimeVersion().isOlderThanOrEqual(vm);
  }

  public boolean isNoneRuntime() {
    return runtime == NoneRuntime.getInstance();
  }

  public void configureApiLevel(TestAppViewBuilder testAppViewBuilder) {
    testAppViewBuilder.setMinApi(apiLevel);
  }

  public void configureApiLevel(TestCompilerBuilder<?, ?, ?, ?, ?> testCompilerBuilder) {
    testCompilerBuilder.setMinApi(apiLevel);
  }

  // TODO(b/270021825): Tests should not access the underlying API level directly, but may be
  //  allowed to query if the api level satisfies some condition.
  @Deprecated
  public AndroidApiLevel getApiLevel() {
    if (runtime.isDex() && apiLevel == null) {
      throw new RuntimeException(
          "Use of getApiLevel without configured API levels for TestParametersCollection.");
    }
    return apiLevel;
  }

  public Path getDefaultAndroidJar() {
    assert isDexRuntime();
    return ToolHelper.getFirstSupportedAndroidJar(getApiLevel());
  }

  public Path getDefaultAndroidJarAbove(AndroidApiLevel minimumCompileApiLevel) {
    assert isDexRuntime();
    return ToolHelper.getFirstSupportedAndroidJar(getApiLevel().max(minimumCompileApiLevel));
  }

  public Path getDefaultRuntimeLibrary() {
    return isCfRuntime() ? ToolHelper.getJava8RuntimeJar() : getDefaultAndroidJar();
  }

  // Access to underlying runtime/wrapper.
  public TestRuntime getRuntime() {
    return runtime;
  }

  public boolean isOrSimulateNoneRuntime() {
    return isNoneRuntime()
        || (runtime != null && runtime.equals(TestRuntime.getDefaultCfRuntime()));
  }

  // Helper function to get the "backend" for a given runtime target.
  public Backend getBackend() {
    return runtime.getBackend();
  }

  @Override
  public String toString() {
    if (apiLevel != null) {
      return runtime.toString() + ", api:" + apiLevel.getLevel();
    }
    return runtime.toString();
  }

  public void assertCfRuntime() {
    assertTrue(runtime.isCf());
  }

  public void assertNoneRuntime() {
    assertEquals(NoneRuntime.getInstance(), runtime);
  }

  public void assertIsRepresentativeApiLevelForRuntime() {
    assertTrue(representativeApiLevelForRuntime);
  }

  public TestParameters assumeCfRuntime() {
    assumeTrue(isCfRuntime());
    return this;
  }

  public TestParameters assumeDexRuntime() {
    assumeTrue(isDexRuntime());
    return this;
  }

  public TestParameters assumeIsOrSimulateNoneRuntime() {
    assumeTrue(isOrSimulateNoneRuntime());
    return this;
  }

  public boolean isJvmTestParameters() {
    if (isCfRuntime()) {
      return apiLevel == null || representativeApiLevelForRuntime;
    }
    return false;
  }

  public TestParameters assumeJvmTestParameters() {
    assumeCfRuntime();
    assumeTrue(apiLevel == null || representativeApiLevelForRuntime);
    return this;
  }

  public TestParameters assumeProguardTestParameters() {
    assumeCfRuntime();
    assumeTrue(apiLevel == null || representativeApiLevelForRuntime);
    return this;
  }

  public TestParameters assumeR8TestParameters() {
    assumeTrue(isR8TestParameters());
    return this;
  }

  public boolean isR8TestParameters() {
    assertFalse(
        "No need to use is/assumeR8TestParameters() when not using api levels for CF",
        isCfRuntime() && apiLevel == null);
    assertTrue(apiLevel != null || representativeApiLevelForRuntime);
    return isDexRuntime() || representativeApiLevelForRuntime;
  }

  public TestParameters assumeRuntimeTestParameters() {
    assertFalse(
        "No need to use assumeRuntimeTestParameters() when not using api levels for CF",
        apiLevel == null);
    assumeTrue(isDexRuntime() || representativeApiLevelForRuntime);
    return this;
  }

  public DexVm.Version getDexRuntimeVersion() {
    assertTrue(isDexRuntime());
    return getRuntime().asDex().getVm().getVersion();
  }
}
