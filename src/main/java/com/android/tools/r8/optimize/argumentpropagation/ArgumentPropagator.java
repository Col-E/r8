// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

/** Optimization that propagates information about arguments from call sites to method entries. */
public class ArgumentPropagator {

  private final AppView<AppInfoWithLiveness> appView;

  /**
   * Collects information about arguments from call sites, meanwhile pruning redundant information.
   *
   * <p>The data held by this instance is incomplete and should not be used for optimization until
   * processed by {@link ArgumentPropagatorOptimizationInfoPopulator}.
   */
  private ArgumentPropagatorCodeScanner codeScanner;

  public ArgumentPropagator(AppView<AppInfoWithLiveness> appView) {
    assert appView.enableWholeProgramOptimizations();
    assert appView.options().isOptimizing();
    assert appView.options().callSiteOptimizationOptions().isEnabled();
    assert appView
        .options()
        .callSiteOptimizationOptions()
        .isExperimentalArgumentPropagationEnabled();
    this.appView = appView;
  }

  /**
   * Called by {@link IRConverter} *before* the primary optimization pass to setup the scanner for
   * collecting argument information from the code objects.
   */
  public void initializeCodeScanner() {
    codeScanner = new ArgumentPropagatorCodeScanner(appView);
  }

  /** Called by {@link IRConverter} prior to finalizing methods. */
  public void scan(ProgramMethod method, IRCode code, MethodProcessor methodProcessor) {
    if (codeScanner != null) {
      // TODO(b/190154391): Do we process synthetic methods using a OneTimeMethodProcessor
      //  during the primary optimization pass?
      assert methodProcessor.isPrimaryMethodProcessor();
      codeScanner.scan(method, code);
    } else {
      assert !methodProcessor.isPrimaryMethodProcessor();
    }
  }

  /**
   * Called by {@link IRConverter} *after* the primary optimization pass to populate the parameter
   * optimization info.
   */
  public void populateParameterOptimizationInfo() {
    assert appView.isAllCodeProcessed();
    new ArgumentPropagatorOptimizationInfoPopulator()
        .populateOptimizationInfo(codeScanner.getResult());
    codeScanner = null;
  }

  /**
   * Called by {@link IRConverter} to optimize method definitions. This also adds all methods that
   * require reprocessing to {@param postMethodProcessorBuilder}.
   */
  public void optimizeMethodParameters(PostMethodProcessor.Builder postMethodProcessorBuilder) {
    // TODO(b/190154391): Remove parameters with constant values.
    // TODO(b/190154391): Remove unused parameters by simulating they are constant.
    // TODO(b/190154391): Strengthen the static type of parameters.
    // TODO(b/190154391): If we learn that a method returns a constant, then consider changing its
    //  return type to void.
  }
}
