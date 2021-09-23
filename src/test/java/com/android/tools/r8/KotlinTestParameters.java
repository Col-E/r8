// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinCompilerTool.KotlinTargetVersion;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class KotlinTestParameters {

  private final int index;
  private final KotlinCompiler kotlinc;
  private final KotlinTargetVersion targetVersion;

  private KotlinTestParameters(
      KotlinCompiler kotlinc, KotlinTargetVersion targetVersion, int index) {
    this.index = index;
    this.kotlinc = kotlinc;
    this.targetVersion = targetVersion;
  }

  public KotlinCompiler getCompiler() {
    return kotlinc;
  }

  public KotlinTargetVersion getTargetVersion() {
    return targetVersion;
  }

  public boolean is(KotlinCompilerVersion compilerVersion) {
    return kotlinc.is(compilerVersion);
  }

  public boolean is(KotlinCompilerVersion compilerVersion, KotlinTargetVersion targetVersion) {
    return is(compilerVersion) && this.targetVersion == targetVersion;
  }

  public boolean isFirst() {
    return index == 0;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public String toString() {
    return kotlinc + "[target=" + targetVersion + "]";
  }

  public static class Builder {

    private Predicate<KotlinCompilerVersion> compilerFilter = c -> false;
    private Predicate<KotlinTargetVersion> targetVersionFilter = t -> false;
    private boolean withDevCompiler =
        System.getProperty("com.android.tools.r8.kotlincompilerdev") != null;

    private Builder() {}

    private Builder withCompilerFilter(Predicate<KotlinCompilerVersion> predicate) {
      compilerFilter = compilerFilter.or(predicate);
      return this;
    }

    private Builder withTargetVersionFilter(Predicate<KotlinTargetVersion> predicate) {
      targetVersionFilter = targetVersionFilter.or(predicate);
      return this;
    }

    public Builder withAllCompilers() {
      withCompilerFilter(compiler -> true);
      return this;
    }

    public Builder withAllCompilersAndTargetVersions() {
      return withAllCompilers().withAllTargetVersions();
    }

    public Builder withCompiler(KotlinCompilerVersion compilerVersion) {
      withCompilerFilter(c -> c.isEqualTo(compilerVersion));
      return this;
    }

    public Builder withDevCompiler() {
      this.withDevCompiler = true;
      return this;
    }

    public Builder withAllTargetVersions() {
      withTargetVersionFilter(t -> t != KotlinTargetVersion.NONE);
      return this;
    }

    public Builder withTargetVersion(KotlinTargetVersion targetVersion) {
      withTargetVersionFilter(t -> t.equals(targetVersion));
      return this;
    }

    public Builder withNoTargetVersion() {
      return withTargetVersion(KotlinTargetVersion.NONE);
    }

    public Builder withCompilersStartingFromIncluding(KotlinCompilerVersion version) {
      withCompilerFilter(c -> c.isGreaterThanOrEqualTo(version));
      return this;
    }

    public KotlinTestParametersCollection build() {
      List<KotlinTestParameters> testParameters = new ArrayList<>();
      int index = 0;
      List<KotlinCompilerVersion> compilerVersions;
      if (withDevCompiler) {
        compilerVersions = ImmutableList.of(KotlinCompilerVersion.KOTLIN_DEV);
      } else {
        compilerVersions =
            Arrays.stream(KotlinCompilerVersion.values())
                .filter(c -> c != KotlinCompilerVersion.KOTLIN_DEV && compilerFilter.test(c))
                .collect(Collectors.toList());
      }
      for (KotlinCompilerVersion kotlinVersion : compilerVersions) {
        for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
          // KotlinTargetVersion java 6 is deprecated from kotlinc 1.5 and forward, no need to run
          // tests on that target.
          if (targetVersion == KotlinTargetVersion.JAVA_6
              && kotlinVersion.isGreaterThanOrEqualTo(KotlinCompilerVersion.KOTLINC_1_5_0)) {
            continue;
          }
          if (targetVersionFilter.test(targetVersion)) {
            testParameters.add(
                new KotlinTestParameters(
                    new KotlinCompiler(kotlinVersion), targetVersion, index++));
          }
        }
      }
      assert !testParameters.isEmpty();
      return new KotlinTestParametersCollection(testParameters);
    }
  }
}
