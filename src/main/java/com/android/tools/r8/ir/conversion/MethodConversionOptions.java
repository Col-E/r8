// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.utils.InternalOptions;

public abstract class MethodConversionOptions {

  public static MutableMethodConversionOptions forPreLirPhase(AppView<?> appView) {
    if (!appView.enableWholeProgramOptimizations()) {
      return forD8(appView);
    }
    assert appView.testing().isPreLirPhase();
    return new MutableMethodConversionOptions(Target.CF, appView.options());
  }

  public static MutableMethodConversionOptions forPostLirPhase(AppView<?> appView) {
    if (!appView.enableWholeProgramOptimizations()) {
      return forD8(appView);
    }
    assert appView.testing().isPostLirPhase();
    Target target = appView.options().isGeneratingClassFiles() ? Target.CF : Target.DEX;
    return new MutableMethodConversionOptions(target, appView.options());
  }

  public static MutableMethodConversionOptions forLirPhase(AppView<?> appView) {
    if (!appView.enableWholeProgramOptimizations()) {
      return forD8(appView);
    }
    assert appView.testing().isSupportedLirPhase();
    return new MutableMethodConversionOptions(determineTarget(appView), appView.options());
  }

  public static MutableMethodConversionOptions forD8(AppView<?> appView) {
    assert !appView.enableWholeProgramOptimizations();
    return new MutableMethodConversionOptions(determineTarget(appView), appView.options());
  }

  public static MutableMethodConversionOptions nonConverting() {
    return new ThrowingMethodConversionOptions();
  }

  public IRFinalizer<?> getFinalizer(DeadCodeRemover deadCodeRemover, AppView<?> appView) {
    if (isGeneratingLir()) {
      return new IRToLirFinalizer(appView, deadCodeRemover);
    }
    if (isGeneratingClassFiles()) {
      return new IRToCfFinalizer(appView, deadCodeRemover);
    }
    assert isGeneratingDex();
    return new IRToDexFinalizer(appView, deadCodeRemover);
  }

  public enum Target {
    CF,
    DEX,
    LIR
  }

  public static Target determineTarget(AppView<?> appView) {
    if (appView.testing().canUseLir(appView)) {
      return Target.LIR;
    }
    if (appView.options().isGeneratingClassFiles()) {
      return Target.CF;
    }
    assert appView.options().isGeneratingDex();
    return Target.DEX;
  }

  public abstract boolean isGeneratingLir();

  public abstract boolean isGeneratingClassFiles();

  public abstract boolean isGeneratingDex();

  public abstract boolean isPeepholeOptimizationsEnabled();

  public abstract boolean isStringSwitchConversionEnabled();

  public static class MutableMethodConversionOptions extends MethodConversionOptions {

    private Target target;
    private boolean enablePeepholeOptimizations = true;
    private boolean enableStringSwitchConversion;

    public MutableMethodConversionOptions(Target target, boolean enableStringSwitchConversion) {
      this.target = target;
      this.enableStringSwitchConversion = enableStringSwitchConversion;
    }

    public MutableMethodConversionOptions(Target target, InternalOptions options) {
      this(target, options.isStringSwitchConversionEnabled());
    }

    public void disablePeepholeOptimizations(MethodProcessor methodProcessor) {
      assert methodProcessor.isPrimaryMethodProcessor();
      enablePeepholeOptimizations = false;
    }

    public MutableMethodConversionOptions disableStringSwitchConversion() {
      enableStringSwitchConversion = false;
      return this;
    }

    @Override
    public boolean isGeneratingLir() {
      return target == Target.LIR;
    }

    @Override
    public boolean isGeneratingClassFiles() {
      return target == Target.CF;
    }

    @Override
    public boolean isGeneratingDex() {
      return target == Target.DEX;
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

    private ThrowingMethodConversionOptions() {
      super(null, true);
    }

    @Override
    public boolean isGeneratingLir() {
      throw new Unreachable();
    }

    @Override
    public boolean isGeneratingClassFiles() {
      throw new Unreachable();
    }

    @Override
    public boolean isGeneratingDex() {
      throw new Unreachable();
    }

    @Override
    public boolean isPeepholeOptimizationsEnabled() {
      throw new Unreachable();
    }
  }
}
