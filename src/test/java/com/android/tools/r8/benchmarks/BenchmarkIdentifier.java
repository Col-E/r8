// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.android.tools.r8.utils.structural.Equatable;
import com.android.tools.r8.utils.structural.Ordered;
import java.util.Comparator;
import java.util.Objects;

public class BenchmarkIdentifier implements Ordered<BenchmarkIdentifier> {

  private final String name;
  private final BenchmarkTarget target;

  public static BenchmarkIdentifier parse(String benchmarkName, String targetIdentifier) {
    for (BenchmarkTarget target : BenchmarkTarget.values()) {
      if (target.getIdentifierName().equals(targetIdentifier)) {
        return new BenchmarkIdentifier(benchmarkName, target);
      }
    }
    return null;
  }

  public BenchmarkIdentifier(String name, BenchmarkTarget target) {
    this.name = name;
    this.target = target;
  }

  public String getName() {
    return name;
  }

  public BenchmarkTarget getTarget() {
    return target;
  }

  @Override
  public int compareTo(BenchmarkIdentifier other) {
    return Comparator.comparing(BenchmarkIdentifier::getName)
        .thenComparing(BenchmarkIdentifier::getTarget)
        .compare(this, other);
  }

  @Override
  public boolean equals(Object o) {
    return Equatable.equalsImpl(this, o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, target);
  }

  @Override
  public String toString() {
    return "BenchmarkIdentifier{" + "name='" + name + '\'' + ", target=" + target + '}';
  }
}
