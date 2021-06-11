// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

public abstract class MethodConversionOptions {

  public abstract boolean isPeepholeOptimizationsEnabled();

  public static class MutableMethodConversionOptions extends MethodConversionOptions {

    private final MethodProcessor methodProcessor;
    private boolean enablePeepholeOptimizations = true;

    public MutableMethodConversionOptions(MethodProcessor methodProcessor) {
      this.methodProcessor = methodProcessor;
    }

    public void disablePeepholeOptimizations() {
      assert methodProcessor.isPrimaryMethodProcessor();
      enablePeepholeOptimizations = false;
    }

    @Override
    public boolean isPeepholeOptimizationsEnabled() {
      assert enablePeepholeOptimizations || methodProcessor.isPrimaryMethodProcessor();
      return enablePeepholeOptimizations;
    }
  }

  public static class DefaultMethodConversionOptions extends MethodConversionOptions {

    private static final DefaultMethodConversionOptions INSTANCE =
        new DefaultMethodConversionOptions();

    private DefaultMethodConversionOptions() {}

    public static DefaultMethodConversionOptions getInstance() {
      return INSTANCE;
    }

    @Override
    public boolean isPeepholeOptimizationsEnabled() {
      return true;
    }
  }
}
