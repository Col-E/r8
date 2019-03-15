// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.errors.Unreachable;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TestParametersBuilder {

  // Static computation of VMs configured as available by the testing invocation.
  private static final List<CfVm> availableCfVms = getAvailableCfVms();
  private static final List<DexVm> availableDexVms = getAvailableDexVms();

  // Predicate describing which available CF runtimes are applicable to the test.
  // Built via the methods found below. Default none.
  private Predicate<CfVm> cfRuntimePredicate = vm -> false;

  // Predicate describing which available DEX runtimes are applicable to the test.
  // Built via the methods found below. Default none.
  private Predicate<DexVm.Version> dexRuntimePredicate = vm -> false;

  private TestParametersBuilder() {}

  public static TestParametersBuilder builder() {
    return new TestParametersBuilder();
  }

  public TestParametersBuilder withAllRuntimes() {
    return withCfRuntimes().withDexRuntimes();
  }

  /** Add specific runtime if available. */
  public TestParametersBuilder withCfRuntime(CfVm runtime) {
    cfRuntimePredicate = cfRuntimePredicate.or(vm -> vm == runtime);
    return this;
  }

  /** Add all available CF runtimes. */
  public TestParametersBuilder withCfRuntimes() {
    cfRuntimePredicate = vm -> true;
    return this;
  }

  /** Add all available CF runtimes between {@param startInclusive} and {@param endInclusive}. */
  public TestParametersBuilder withCfRuntimes(CfVm startInclusive, CfVm endInclusive) {
    cfRuntimePredicate =
        cfRuntimePredicate.or(
            vm -> startInclusive.lessThanOrEqual(vm) && vm.lessThanOrEqual(endInclusive));
    return this;
  }

  /** Add all available CF runtimes starting from and including {@param startInclusive}. */
  public TestParametersBuilder withCfRuntimesStartingFromIncluding(CfVm startInclusive) {
    cfRuntimePredicate = cfRuntimePredicate.or(vm -> startInclusive.lessThanOrEqual(vm));
    return this;
  }

  /** Add all available CF runtimes starting from and excluding {@param startExcluding}. */
  public TestParametersBuilder withCfRuntimesStartingFromExcluding(CfVm startExcluding) {
    cfRuntimePredicate = cfRuntimePredicate.or(vm -> startExcluding.lessThan(vm));
    return this;
  }

  /** Add all available CF runtimes ending at and including {@param endInclusive}. */
  public TestParametersBuilder withCfRuntimesEndingAtIncluding(CfVm endInclusive) {
    cfRuntimePredicate = cfRuntimePredicate.or(vm -> vm.lessThanOrEqual(endInclusive));
    return this;
  }

  /** Add all available CF runtimes ending at and excluding {@param endExclusive}. */
  public TestParametersBuilder withCfRuntimesEndingAtExcluding(CfVm endExclusive) {
    cfRuntimePredicate = cfRuntimePredicate.or(vm -> vm.lessThan(endExclusive));
    return this;
  }

  /** Add all available DEX runtimes. */
  public TestParametersBuilder withDexRuntimes() {
    dexRuntimePredicate = vm -> true;
    return this;
  }

  /** Add specific runtime if available. */
  public TestParametersBuilder withDexRuntime(DexVm.Version runtime) {
    dexRuntimePredicate = dexRuntimePredicate.or(vm -> vm == runtime);
    return this;
  }

  /** Add all available CF runtimes between {@param startInclusive} and {@param endInclusive}. */
  public TestParametersBuilder withDexRuntimes(
      DexVm.Version startInclusive, DexVm.Version endInclusive) {
    dexRuntimePredicate =
        dexRuntimePredicate.or(
            vm -> startInclusive.isOlderThanOrEqual(vm) && vm.isOlderThanOrEqual(endInclusive));
    return this;
  }

  /** Add all available DEX runtimes starting from and including {@param startInclusive}. */
  public TestParametersBuilder withDexRuntimesStartingFromIncluding(DexVm.Version startInclusive) {
    dexRuntimePredicate = dexRuntimePredicate.or(vm -> startInclusive.isOlderThanOrEqual(vm));
    return this;
  }

  /** Add all available DEX runtimes starting from and excluding {@param startExcluding}. */
  public TestParametersBuilder withDexRuntimesStartingFromExcluding(DexVm.Version startExcluding) {
    dexRuntimePredicate =
        dexRuntimePredicate.or(vm -> vm != startExcluding && startExcluding.isOlderThanOrEqual(vm));
    return this;
  }

  /** Add all available DEX runtimes ending at and including {@param endInclusive}. */
  public TestParametersBuilder withDexRuntimesEndingAtIncluding(DexVm.Version endInclusive) {
    dexRuntimePredicate = dexRuntimePredicate.or(vm -> vm.isOlderThanOrEqual(endInclusive));
    return this;
  }

  /** Add all available DEX runtimes ending at and excluding {@param endExclusive}. */
  public TestParametersBuilder withDexRuntimesEndingAtExcluding(DexVm.Version endExclusive) {
    dexRuntimePredicate =
        dexRuntimePredicate.or(vm -> vm != endExclusive && vm.isOlderThanOrEqual(endExclusive));
    return this;
  }

  public TestParametersCollection build() {
    ImmutableList.Builder<TestParameters> parameters = ImmutableList.builder();
    availableCfVms.stream()
        .filter(cfRuntimePredicate)
        .forEach(vm -> parameters.add(new TestParameters(new CfRuntime(vm))));
    availableDexVms.stream()
        .filter(vm -> dexRuntimePredicate.test(vm.getVersion()))
        .forEach(vm -> parameters.add(new TestParameters(new DexRuntime(vm))));
    return new TestParametersCollection(parameters.build());
  }

  // Public method to check that the CF runtime coincides with the system runtime.
  public static boolean isSystemJdk(CfVm vm) {
    String version = System.getProperty("java.version");
    switch (vm) {
      case JDK8:
        return version.startsWith("1.8.");
      case JDK9:
        return version.startsWith("9.");
    }
    throw new Unreachable();
  }

  // Currently the only supported VM is the system VM. This should be extended to start supporting
  // the checked in versions too, making it possible to run tests on more than one JDK at a time.
  private static boolean isSupportedJdk(CfVm vm) {
    return isSystemJdk(vm);
  }

  public static List<CfVm> getAvailableCfVms() {
    String cfVmsProperty = System.getProperty("cf_vms");
    if (cfVmsProperty != null) {
      return Arrays.stream(cfVmsProperty.split(":"))
          .filter(s -> !s.isEmpty())
          .map(TestRuntime.CfVm::fromName)
          .filter(TestParametersBuilder::isSupportedJdk)
          .collect(Collectors.toList());
    } else {
      // TODO(b/127785410) Support multiple VMs at the same time.
      return Arrays.stream(TestRuntime.CfVm.values())
          .filter(TestParametersBuilder::isSystemJdk)
          .collect(Collectors.toList());
    }
  }

  public static List<DexVm> getAvailableDexVms() {
    String dexVmsProperty = System.getProperty("dex_vms");
    if (dexVmsProperty != null) {
      return Arrays.stream(dexVmsProperty.split(":"))
          .filter(s -> !s.isEmpty())
          .map(v -> DexVm.fromShortName(v + "_host"))
          .collect(Collectors.toList());
    } else {
      return Arrays.stream(DexVm.Version.values())
          .map(v -> DexVm.fromShortName(v.toString() + "_host"))
          .collect(Collectors.toList());
    }
  }
}
