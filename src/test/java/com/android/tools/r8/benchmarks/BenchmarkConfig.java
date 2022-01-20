// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.android.tools.r8.errors.Unreachable;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.rules.TemporaryFolder;

public class BenchmarkConfig {

  public static class Builder {

    private String name = null;
    private BenchmarkMethod method = null;
    private BenchmarkTarget target = null;
    private Set<BenchmarkMetric> metrics = new HashSet<>();
    private BenchmarkSuite suite = BenchmarkSuite.getDefault();
    private int fromRevision = -1;

    private boolean timeWarmupRuns = false;

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
      if (timeWarmupRuns && !metrics.contains(BenchmarkMetric.RunTimeRaw)) {
        throw new Unreachable("Benchmark with warmup time must measure RunTimeRaw");
      }
      return new BenchmarkConfig(
          name, method, target, ImmutableSet.copyOf(metrics), suite, fromRevision, timeWarmupRuns);
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

    public Builder measureRunTimeRaw() {
      metrics.add(BenchmarkMetric.RunTimeRaw);
      return this;
    }

    public Builder measureCodeSize() {
      metrics.add(BenchmarkMetric.CodeSize);
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

    public Builder timeWarmupRuns() {
      this.timeWarmupRuns = true;
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
  private final int fromRevision;
  private final boolean timeWarmupRuns;

  private BenchmarkConfig(
      String name,
      BenchmarkMethod benchmarkMethod,
      BenchmarkTarget target,
      ImmutableSet<BenchmarkMetric> metrics,
      BenchmarkSuite suite,
      int fromRevision,
      boolean timeWarmupRuns) {
    this.id = new BenchmarkIdentifier(name, target);
    this.method = benchmarkMethod;
    this.metrics = metrics;
    this.suite = suite;
    this.fromRevision = fromRevision;
    this.timeWarmupRuns = timeWarmupRuns;
  }

  public BenchmarkIdentifier getIdentifier() {
    return id;
  }

  public String getName() {
    return id.getName();
  }

  public String getWarmupName() {
    if (!timeWarmupRuns) {
      throw new Unreachable("Invalid attempt at getting warmup benchmark name");
    }
    return getName() + "Warmup";
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
    return timeWarmupRuns;
  }

  public void run(TemporaryFolder temp) throws Exception {
    method.run(this, temp);
  }

  @Override
  public String toString() {
    return id.getName() + "/" + id.getTarget().getIdentifierName();
  }
}
