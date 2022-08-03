// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.InternalOptions;

public abstract class MethodConversionOptions {

  public abstract boolean isGeneratingClassFiles();

  public final boolean isGeneratingDex() {
    return !isGeneratingClassFiles();
  }

  public abstract boolean isPeepholeOptimizationsEnabled();

  public abstract boolean isStringSwitchConversionEnabled();

  public static class MutableMethodConversionOptions extends MethodConversionOptions {

    private boolean enablePeepholeOptimizations = true;
    private boolean enableStringSwitchConversion;
    private boolean isGeneratingClassFiles;

    public MutableMethodConversionOptions(InternalOptions options) {
      this.enableStringSwitchConversion = options.isStringSwitchConversionEnabled();
      this.isGeneratingClassFiles = options.isGeneratingClassFiles();
    }

    public void disablePeepholeOptimizations(MethodProcessor methodProcessor) {
      assert methodProcessor.isPrimaryMethodProcessor();
      enablePeepholeOptimizations = false;
    }

    public MutableMethodConversionOptions disableStringSwitchConversion() {
      enableStringSwitchConversion = false;
      return this;
    }

    public MutableMethodConversionOptions setIsGeneratingClassFiles(
        boolean isGeneratingClassFiles) {
      this.isGeneratingClassFiles = isGeneratingClassFiles;
      return this;
    }

    @Override
    public boolean isGeneratingClassFiles() {
      return isGeneratingClassFiles;
    }

    @Override
    public boolean isPeepholeOptimizationsEnabled() {
      return enablePeepholeOptimizations;
    }

    @Override
    public boolean isStringSwitchConversionEnabled() {
      return enableStringSwitchConversion;
    }
  }

  public static class ThrowingMethodConversionOptions extends MutableMethodConversionOptions {

    public ThrowingMethodConversionOptions(InternalOptions options) {
      super(options);
    }

    @Override
    public boolean isGeneratingClassFiles() {
      throw new Unreachable();
    }

    @Override
    public boolean isPeepholeOptimizationsEnabled() {
      throw new Unreachable();
    }
  }
}
