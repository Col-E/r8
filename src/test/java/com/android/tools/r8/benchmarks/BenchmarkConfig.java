// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.android.tools.r8.errors.Unreachable;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BenchmarkConfig {

  public static void checkBenchmarkConsistency(BenchmarkConfig benchmark, BenchmarkConfig other) {
    if (benchmark.getTarget().equals(other.getTarget())) {
      throw new BenchmarkConfigError("Duplicate benchmark name and target: " + benchmark);
    }
    if (!benchmark.getMetrics().equals(other.getMetrics())) {
      throw new BenchmarkConfigError(
          "Inconsistent metrics for benchmarks: " + benchmark + " and " + other);
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
    private BenchmarkSuite suite = BenchmarkSuite.getDefault();
    private Collection<BenchmarkDependency> dependencies = new ArrayList<>();
    private int fromRevision = -1;

    private Builder() {}

    public BenchmarkConfig build() {
      if (name == null) {
        throw new Unreachable("Benchmark name must be set");
      }
      if (method == null) {
        throw new Unreachable("Benchmark method must be set");
      }
      if (target == null) {
        throw new Unreachable("Benchmark target must be set");
      }
      if (metrics.isEmpty()) {
        throw new Unreachable("Benchmark must have at least one metric to measure");
      }
      if (suite == null) {
        throw new Unreachable("Benchmark must have a suite");
      }
      if (fromRevision < 0) {
        throw new Unreachable("Benchmark must specify from which golem revision it is valid");
      }
      return new BenchmarkConfig(
          name,
          method,
          target,
          ImmutableSet.copyOf(metrics),
          suite,
          fromRevision,
          dependencies);
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
      metrics.add(BenchmarkMetric.StartupTime);
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
  }

  public static Builder builder() {
    return new Builder();
  }

  private final BenchmarkIdentifier id;
  private final BenchmarkMethod method;
  private final ImmutableSet<BenchmarkMetric> metrics;
  private final BenchmarkSuite suite;
  private final Collection<BenchmarkDependency> dependencies;
  private final int fromRevision;

  private BenchmarkConfig(
      String name,
      BenchmarkMethod benchmarkMethod,
      BenchmarkTarget target,
      ImmutableSet<BenchmarkMetric> metrics,
      BenchmarkSuite suite,
      int fromRevision,
      Collection<BenchmarkDependency> dependencies) {
    this.id = new BenchmarkIdentifier(name, target);
    this.method = benchmarkMethod;
    this.metrics = metrics;
    this.suite = suite;
    this.fromRevision = fromRevision;
    this.dependencies = dependencies;
  }

  public BenchmarkIdentifier getIdentifier() {
    return id;
  }

  public String getName() {
    return id.getName();
  }

  public BenchmarkTarget getTarget() {
    return id.getTarget();
  }

  public Set<BenchmarkMetric> getMetrics() {
    return metrics;
  }

  public boolean hasMetric(BenchmarkMetric metric) {
    return metrics.contains(metric);
  }

  public BenchmarkSuite getSuite() {
    return suite;
  }

  public int getFromRevision() {
    return fromRevision;
  }

  public boolean hasTimeWarmupRuns() {
    return hasMetric(BenchmarkMetric.StartupTime);
  }

  public Collection<BenchmarkDependency> getDependencies() {
    return dependencies;
  }

  public void run(BenchmarkEnvironment environment) throws Exception {
    method.run(environment);
  }

  @Override
  public String toString() {
    return id.getName() + "/" + id.getTarget().getIdentifierName();
  }
}
