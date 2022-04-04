// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class BenchmarkConfig {

  public static void checkBenchmarkConsistency(BenchmarkConfig benchmark, BenchmarkConfig other) {
    if (benchmark.getTarget().equals(other.getTarget())) {
      throw new BenchmarkConfigError("Duplicate benchmark name and target: " + benchmark);
    }
    if (benchmark.isSingleBenchmark() != other.isSingleBenchmark()) {
      throw new BenchmarkConfigError(
          "Inconsistent single/group benchmark setup: " + benchmark + " and " + other);
    }
    Set<String> subNames =
        Sets.union(benchmark.getSubBenchmarks().keySet(), other.getSubBenchmarks().keySet());
    for (String subName : subNames) {
      if (!Objects.equals(
          benchmark.getSubBenchmarks().get(subName), other.getSubBenchmarks().get(subName))) {
        throw new BenchmarkConfigError(
            "Inconsistent metrics for sub-benchmark "
                + subName
                + " in benchmarks: "
                + benchmark
                + " and "
                + other);
      }
    }
    if (!benchmark.getSuite().equals(other.getSuite())) {
      throw new BenchmarkConfigError(
          "Inconsistent suite for benchmarks: " + benchmark + " and " + other);
    }
    if (benchmark.hasTimeWarmupRuns() != other.hasTimeWarmupRuns()) {
      throw new BenchmarkConfigError(
          "Inconsistent time-warmup for benchmarks: " + benchmark + " and " + other);
    }
  }

  public static Set<BenchmarkMetric> getCommonMetrics(List<BenchmarkConfig> variants) {
    return getConsistentRepresentative(variants).getMetrics();
  }

  public static BenchmarkSuite getCommonSuite(List<BenchmarkConfig> variants) {
    return getConsistentRepresentative(variants).getSuite();
  }

  public static boolean getCommonHasTimeWarmup(List<BenchmarkConfig> variants) {
    return getConsistentRepresentative(variants).hasTimeWarmupRuns();
  }

  public static Map<String, Set<BenchmarkMetric>> getCommonSubBenchmarks(
      List<BenchmarkConfig> variants) {
    return getConsistentRepresentative(variants).getSubBenchmarks();
  }

  // Use the largest configured timeout as the timeout for the full group.
  public static BenchmarkTimeout getCommonTimeout(List<BenchmarkConfig> variants) {
    BenchmarkTimeout timeout = null;
    for (BenchmarkConfig variant : variants) {
      BenchmarkTimeout variantTimeout = variant.getTimeout();
      if (timeout == null) {
        timeout = variantTimeout;
      } else if (variantTimeout != null && timeout.asSeconds() < variantTimeout.asSeconds()) {
        timeout = variantTimeout;
      }
    }
    return timeout;
  }

  private static BenchmarkConfig getConsistentRepresentative(List<BenchmarkConfig> variants) {
    if (variants.isEmpty()) {
      throw new BenchmarkConfigError("Unexpected attempt to check consistency of empty collection");
    }
    BenchmarkConfig representative = variants.get(0);
    for (int i = 1; i < variants.size(); i++) {
      checkBenchmarkConsistency(representative, variants.get(i));
    }
    return representative;
  }

  public static class Builder {

    private String name = null;
    private BenchmarkMethod method = null;
    private BenchmarkTarget target = null;
    private Set<BenchmarkMetric> metrics = new HashSet<>();
    private Map<String, Set<BenchmarkMetric>> subBenchmarks = new HashMap<>();
    private BenchmarkSuite suite = BenchmarkSuite.getDefault();
    private Collection<BenchmarkDependency> dependencies = new ArrayList<>();
    private int fromRevision = -1;
    private BenchmarkTimeout timeout = null;
    private boolean measureWarmup = false;

    private Builder() {}

    public BenchmarkConfig build() {
      if (name == null) {
        throw new BenchmarkConfigError("Benchmark name must be set");
      }
      if (method == null) {
        throw new BenchmarkConfigError("Benchmark method must be set");
      }
      if (target == null) {
        throw new BenchmarkConfigError("Benchmark target must be set");
      }
      if (suite == null) {
        throw new BenchmarkConfigError("Benchmark must have a suite");
      }
      if (fromRevision < 0) {
        throw new BenchmarkConfigError(
            "Benchmark must specify from which golem revision it is valid");
      }
      if (!metrics.isEmpty()) {
        if (subBenchmarks.containsKey(name)) {
          throw new BenchmarkConfigError(
              "Benchmark must not specify both direct metrics and a sub-benchmark of the same"
                  + " name");
        }
        subBenchmarks.put(name, ImmutableSet.copyOf(metrics));
      }
      if (subBenchmarks.isEmpty()) {
        throw new BenchmarkConfigError(
            "Benchmark must have at least one metric / sub-benchmark to measure");
      }
      if (measureWarmup) {
        for (Set<BenchmarkMetric> subMetrics : subBenchmarks.values()) {
          if (subMetrics.contains(BenchmarkMetric.StartupTime)) {
            throw new BenchmarkConfigError(
                "Benchmark cannot both measure warmup and set metric: "
                    + BenchmarkMetric.StartupTime);
          }
        }
      }
      return new BenchmarkConfig(
          name,
          method,
          target,
          subBenchmarks,
          suite,
          fromRevision,
          dependencies,
          timeout,
          measureWarmup);
    }

    public Builder setName(String name) {
      this.name = name;
      return this;
    }

    public Builder setTarget(BenchmarkTarget target) {
      this.target = target;
      return this;
    }

    public Builder setMethod(BenchmarkMethod method) {
      this.method = method;
      return this;
    }

    public Builder measureRunTime() {
      metrics.add(BenchmarkMetric.RunTimeRaw);
      return this;
    }

    public Builder measureCodeSize() {
      metrics.add(BenchmarkMetric.CodeSize);
      return this;
    }

    public Builder measureWarmup() {
      measureWarmup = true;
      return this;
    }

    public Builder addSubBenchmark(String name, BenchmarkMetric... metrics) {
      return addSubBenchmark(name, new HashSet<>(Arrays.asList(metrics)));
    }

    public Builder addSubBenchmark(String name, Set<BenchmarkMetric> metrics) {
      subBenchmarks.put(name, metrics);
      return this;
    }

    public Builder setSuite(BenchmarkSuite suite) {
      this.suite = suite;
      return this;
    }

    public Builder setFromRevision(int fromRevision) {
      this.fromRevision = fromRevision;
      return this;
    }

    public Builder addDependency(BenchmarkDependency dependency) {
      dependencies.add(dependency);
      return this;
    }

    public Builder setTimeout(long duration, TimeUnit unit) {
      timeout = new BenchmarkTimeout(duration, unit);
      return this;
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private final BenchmarkIdentifier id;
  private final BenchmarkMethod method;
  private final Map<String, Set<BenchmarkMetric>> benchmarks;
  private final BenchmarkSuite suite;
  private final Collection<BenchmarkDependency> dependencies;
  private final int fromRevision;
  private final BenchmarkTimeout timeout;
  private final boolean measureWarmup;

  private BenchmarkConfig(
      String name,
      BenchmarkMethod benchmarkMethod,
      BenchmarkTarget target,
      Map<String, Set<BenchmarkMetric>> benchmarks,
      BenchmarkSuite suite,
      int fromRevision,
      Collection<BenchmarkDependency> dependencies,
      BenchmarkTimeout timeout,
      boolean measureWarmup) {
    this.id = new BenchmarkIdentifier(name, target);
    this.method = benchmarkMethod;
    this.benchmarks = benchmarks;
    this.suite = suite;
    this.fromRevision = fromRevision;
    this.dependencies = dependencies;
    this.timeout = timeout;
    this.measureWarmup = measureWarmup;
  }

  public BenchmarkIdentifier getIdentifier() {
    return id;
  }

  public String getName() {
    return id.getName();
  }

  public String getDependencyDirectoryName() {
    return getName();
  }

  public BenchmarkTarget getTarget() {
    return id.getTarget();
  }

  public boolean isSingleBenchmark() {
    return benchmarks.size() == 1 && benchmarks.containsKey(getName());
  }

  public Map<String, Set<BenchmarkMetric>> getSubBenchmarks() {
    return benchmarks;
  }

  public Set<BenchmarkMetric> getMetrics() {
    if (!isSingleBenchmark()) {
      throw new BenchmarkConfigError("Attempt to get single metrics set from group benchmark");
    }
    return benchmarks.get(getName());
  }

  public BenchmarkSuite getSuite() {
    return suite;
  }

  public int getFromRevision() {
    return fromRevision;
  }

  public boolean hasTimeWarmupRuns() {
    return measureWarmup;
  }

  public Collection<BenchmarkDependency> getDependencies() {
    return dependencies;
  }

  public BenchmarkTimeout getTimeout() {
    return timeout;
  }

  public void run(BenchmarkEnvironment environment) throws Exception {
    method.run(environment);
  }

  @Override
  public String toString() {
    return id.getName() + "/" + id.getTarget().getIdentifierName();
  }
}
