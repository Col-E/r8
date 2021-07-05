// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import java.util.ArrayList;
import java.util.List;

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

    private KotlinCompiler[] compilers;
    private KotlinTargetVersion[] targetVersions;

    private Builder() {}

    public Builder withAllCompilers() {
      compilers = ToolHelper.getKotlinCompilers();
      return this;
    }

    public Builder withAllCompilersAndTargetVersions() {
      return withAllCompilers().withAllTargetVersions();
    }

    public Builder withCompiler(KotlinCompiler compiler) {
      compilers = new KotlinCompiler[] {compiler};
      return this;
    }

    public Builder withAllTargetVersions() {
      targetVersions = KotlinTargetVersion.values();
      return this;
    }

    public Builder withTargetVersion(KotlinTargetVersion targetVersion) {
      targetVersions = new KotlinTargetVersion[] {targetVersion};
      return this;
    }

    public KotlinTestParametersCollection build() {
      validate();
      List<KotlinTestParameters> testParameters = new ArrayList<>();
      int index = 0;
      for (KotlinCompiler kotlinc : compilers) {
        for (KotlinTargetVersion targetVersion : targetVersions) {
          // KotlinTargetVersion java 6 is deprecated from kotlinc 1.5 and forward, no need to run
          // tests on that target.
          if (targetVersion != KotlinTargetVersion.JAVA_6
              || kotlinc.isNot(KotlinCompilerVersion.KOTLINC_1_5_0)) {
            testParameters.add(new KotlinTestParameters(kotlinc, targetVersion, index++));
          }
        }
      }
      return new KotlinTestParametersCollection(testParameters);
    }

    private void validate() {
      assertNotNull(compilers);
      assertNotNull(targetVersions);
    }
  }
}
