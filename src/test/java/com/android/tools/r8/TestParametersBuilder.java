// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.TestRuntime.NoneRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestParametersBuilder {

  // Static computation of VMs configured as available by the testing invocation.
  private static final List<TestRuntime> availableRuntimes =
      getAvailableRuntimes().collect(Collectors.toList());

  // Predicate describing which test parameters are applicable to the test.
  // Built via the methods found below. Defaults to no applicable parameters, i.e., the emtpy set.
  private Predicate<TestParameters> filter = param -> false;
  private boolean hasDexRuntimeFilter = false;

  private TestParametersBuilder() {}

  public static TestParametersBuilder builder() {
    return new TestParametersBuilder();
  }

  private TestParametersBuilder withFilter(Predicate<TestParameters> predicate) {
    filter = filter.or(predicate);
    return this;
  }

  private TestParametersBuilder withCfRuntimeFilter(Predicate<CfVm> predicate) {
    return withFilter(p -> p.isCfRuntime() && predicate.test(p.getRuntime().asCf().getVm()));
  }

  private TestParametersBuilder withDexRuntimeFilter(Predicate<DexVm.Version> predicate) {
    hasDexRuntimeFilter = true;
    return withFilter(
        p -> p.isDexRuntime() && predicate.test(p.getRuntime().asDex().getVm().getVersion()));
  }

  public TestParametersBuilder withNoneRuntime() {
    return withFilter(p -> p.getRuntime() == NoneRuntime.getInstance());
  }

  public TestParametersBuilder withAllRuntimes() {
    return withCfRuntimes().withDexRuntimes();
  }

  public TestParametersBuilder withAllRuntimesAndApiLevels() {
    return withCfRuntimes().withDexRuntimes().withAllApiLevels();
  }

  /** Add specific runtime if available. */
  public TestParametersBuilder withCfRuntime(CfVm runtime) {
    return withCfRuntimeFilter(vm -> vm == runtime);
  }

  /** Add all available CF runtimes. */
  public TestParametersBuilder withCfRuntimes() {
    return withCfRuntimeFilter(vm -> true);
  }

  /** Add all available CF runtimes between {@param startInclusive} and {@param endInclusive}. */
  public TestParametersBuilder withCfRuntimes(CfVm startInclusive, CfVm endInclusive) {
    return withCfRuntimeFilter(
        vm -> startInclusive.lessThanOrEqual(vm) && vm.lessThanOrEqual(endInclusive));
  }

  /** Add all available CF runtimes starting from and including {@param startInclusive}. */
  public TestParametersBuilder withCfRuntimesStartingFromIncluding(CfVm startInclusive) {
    return withCfRuntimeFilter(vm -> startInclusive.lessThanOrEqual(vm));
  }

  /** Add all available CF runtimes starting from and excluding {@param startExcluding}. */
  public TestParametersBuilder withCfRuntimesStartingFromExcluding(CfVm startExcluding) {
    return withCfRuntimeFilter(vm -> startExcluding.lessThan(vm));
  }

  /** Add all available CF runtimes ending at and including {@param endInclusive}. */
  public TestParametersBuilder withCfRuntimesEndingAtIncluding(CfVm endInclusive) {
    return withCfRuntimeFilter(vm -> vm.lessThanOrEqual(endInclusive));
  }

  /** Add all available CF runtimes ending at and excluding {@param endExclusive}. */
  public TestParametersBuilder withCfRuntimesEndingAtExcluding(CfVm endExclusive) {
    return withCfRuntimeFilter(vm -> vm.lessThan(endExclusive));
  }

  /** Add all available DEX runtimes. */
  public TestParametersBuilder withDexRuntimes() {
    return withDexRuntimeFilter(vm -> true);
  }

  /** Add specific runtime if available. */
  public TestParametersBuilder withDexRuntime(DexVm.Version runtime) {
    return withDexRuntimeFilter(vm -> vm == runtime);
  }

  /** Add all available CF runtimes between {@param startInclusive} and {@param endInclusive}. */
  public TestParametersBuilder withDexRuntimes(
      DexVm.Version startInclusive, DexVm.Version endInclusive) {
    return withDexRuntimeFilter(
        vm -> startInclusive.isOlderThanOrEqual(vm) && vm.isOlderThanOrEqual(endInclusive));
  }

  /** Add all available DEX runtimes that support native multidex. */
  public TestParametersBuilder withNativeMultidexDexRuntimes() {
    return withDexRuntimesStartingFromIncluding(DexVm.Version.V5_1_1);
  }

  /** Add all available DEX runtimes starting from and including {@param startInclusive}. */
  public TestParametersBuilder withDexRuntimesStartingFromIncluding(DexVm.Version startInclusive) {
    return withDexRuntimeFilter(vm -> startInclusive.isOlderThanOrEqual(vm));
  }

  /** Add all available DEX runtimes starting from and excluding {@param startExcluding}. */
  public TestParametersBuilder withDexRuntimesStartingFromExcluding(DexVm.Version startExcluding) {
    return withDexRuntimeFilter(
        vm -> vm != startExcluding && startExcluding.isOlderThanOrEqual(vm));
  }

  /** Add all available DEX runtimes ending at and including {@param endInclusive}. */
  public TestParametersBuilder withDexRuntimesEndingAtIncluding(DexVm.Version endInclusive) {
    return withDexRuntimeFilter(vm -> vm.isOlderThanOrEqual(endInclusive));
  }

  /** Add all available DEX runtimes ending at and excluding {@param endExclusive}. */
  public TestParametersBuilder withDexRuntimesEndingAtExcluding(DexVm.Version endExclusive) {
    return withDexRuntimeFilter(vm -> vm != endExclusive && vm.isOlderThanOrEqual(endExclusive));
  }

  /**
   * API level configuration.
   *
   * <p>Currently enabling API level config will by default configure each DEX VM to be configured
   * with two parameters, one running at the highest api-level supported by the VM and one at the
   * lowest supported by the compiler (i.e., B).
   */
  private static final AndroidApiLevel lowestCompilerApiLevel = AndroidApiLevel.B;

  private boolean enableApiLevels = false;
  private boolean enableApiLevelsForCf = false;

  private Predicate<AndroidApiLevel> apiLevelFilter = param -> false;
  private List<AndroidApiLevel> explicitApiLevels = new ArrayList<>();

  private TestParametersBuilder withApiFilter(Predicate<AndroidApiLevel> filter) {
    enableApiLevels = true;
    apiLevelFilter = apiLevelFilter.or(filter);
    return this;
  }

  public TestParametersBuilder withAllApiLevels() {
    return withApiFilter(api -> true);
  }

  public TestParametersBuilder withAllApiLevelsAlsoForCf() {
    enableApiLevelsForCf = true;
    return withAllApiLevels();
  }

  public TestParametersBuilder withApiLevel(AndroidApiLevel api) {
    explicitApiLevels.add(api);
    return withApiFilter(api::equals);
  }

  public TestParametersBuilder withApiLevelsStartingAtIncluding(AndroidApiLevel startInclusive) {
    return withApiFilter(api -> startInclusive.getLevel() <= api.getLevel());
  }

  public TestParametersBuilder withApiLevelsStartingAtExcluding(AndroidApiLevel startExclusive) {
    return withApiFilter(api -> startExclusive.getLevel() < api.getLevel());
  }

  public TestParametersBuilder withApiLevelsEndingAtIncluding(AndroidApiLevel endInclusive) {
    return withApiFilter(api -> api.getLevel() <= endInclusive.getLevel());
  }

  public TestParametersBuilder withApiLevelsEndingAtExcluding(AndroidApiLevel endExclusive) {
    return withApiFilter(api -> api.getLevel() < endExclusive.getLevel());
  }

  public TestParametersCollection build() {
    assert !enableApiLevels || enableApiLevelsForCf || hasDexRuntimeFilter;
    return new TestParametersCollection(
        getAvailableRuntimes()
            .flatMap(this::createParameters)
            .filter(filter)
            .collect(Collectors.toList()));
  }

  public Stream<TestParameters> createParameters(TestRuntime runtime) {
    if (!enableApiLevels) {
      return Stream.of(new TestParameters(runtime));
    }
    if (!runtime.isDex()) {
      if (!enableApiLevelsForCf) {
        return Stream.of(new TestParameters(runtime));
      }
      return Stream.of(
          new TestParameters(runtime, AndroidApiLevel.B),
          new TestParameters(runtime, AndroidApiLevel.LATEST));
    }
    List<AndroidApiLevel> sortedApiLevels =
        AndroidApiLevel.getAndroidApiLevelsSorted().stream()
            .filter(apiLevelFilter)
            .collect(Collectors.toList());
    if (sortedApiLevels.isEmpty()) {
      return Stream.of();
    }
    AndroidApiLevel vmLevel = runtime.asDex().getMinApiLevel();
    AndroidApiLevel lowestApplicable = sortedApiLevels.get(0);
    if (vmLevel.getLevel() < lowestApplicable.getLevel()) {
      return Stream.of();
    }
    if (sortedApiLevels.size() > 1) {
      for (int i = sortedApiLevels.size() - 1; i >= 0; i--) {
        AndroidApiLevel highestApplicable = sortedApiLevels.get(i);
        if (highestApplicable.getLevel() <= vmLevel.getLevel()
            && lowestApplicable != highestApplicable) {
          Set<AndroidApiLevel> set = new TreeSet<>();
          set.add(lowestApplicable);
          set.add(highestApplicable);
          for (AndroidApiLevel explicitApiLevel : explicitApiLevels) {
            if (explicitApiLevel.getLevel() <= vmLevel.getLevel()) {
              set.add(explicitApiLevel);
            }
          }
          return set.stream().map(api -> new TestParameters(runtime, api));
        }
      }
    }
    return Stream.of(new TestParameters(runtime, lowestApplicable));
  }

  // Public method to check that the CF runtime coincides with the system runtime.
  public static boolean isSystemJdk(CfVm vm) {
    TestRuntime systemRuntime = TestRuntime.getSystemRuntime();
    return systemRuntime.isCf() && systemRuntime.asCf().getVm().equals(vm);
  }

  public static boolean isRuntimesPropertySet() {
    return getRuntimesProperty() != null;
  }

  public static String getRuntimesProperty() {
    return System.getProperty("runtimes");
  }

  private static Stream<TestRuntime> getUnfilteredAvailableRuntimes() {
    // The runtimes are built in a linked hash map to ensure a deterministic order and avoid
    // duplicates.
    LinkedHashMap<TestRuntime, TestRuntime> runtimes = new LinkedHashMap<>();
    // Place the none-runtime first.
    NoneRuntime noneRuntime = NoneRuntime.getInstance();
    runtimes.putIfAbsent(noneRuntime, noneRuntime);
    // Then the checked in runtimes (CF and DEX).
    for (TestRuntime checkedInRuntime : TestRuntime.getCheckedInRuntimes()) {
      runtimes.putIfAbsent(checkedInRuntime, checkedInRuntime);
    }
    // Then finally the system runtime. It will likely be the same as a checked in and adding it
    // makes the overall order more stable.
    TestRuntime systemRuntime = TestRuntime.getSystemRuntime();
    runtimes.putIfAbsent(systemRuntime, systemRuntime);
    return runtimes.values().stream();
  }

  private static Stream<TestRuntime> getAvailableRuntimes() {
    if (isRuntimesPropertySet()) {
      String[] runtimeFilters = getRuntimesProperty().split(":");
      return getUnfilteredAvailableRuntimes()
          .filter(
              runtime ->
                  Arrays.stream(runtimeFilters).anyMatch(filter -> runtime.name().equals(filter)));
    }
    return getUnfilteredAvailableRuntimes();
  }

  public static List<CfVm> getAvailableCfVms() {
    return getAvailableRuntimes()
        .filter(TestRuntime::isCf)
        .map(runtime -> runtime.asCf().getVm())
        .collect(Collectors.toList());
  }

  public static List<DexVm> getAvailableDexVms() {
    return getAvailableRuntimes()
        .filter(TestRuntime::isDex)
        .map(runtime -> runtime.asDex().getVm())
        .collect(Collectors.toList());
  }
}
