// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.optimize.Inliner.InlineResult;
import com.android.tools.r8.ir.optimize.inliner.InliningIRProvider;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;

/**
 * The InliningOracle contains information needed for when inlining other methods into @method.
 */
public interface InliningOracle {

  boolean isForcedInliningOracle();

  // TODO(b/142116551): This should be equivalent to invoke.lookupSingleTarget(appView, context)!
  ProgramMethod lookupSingleTarget(InvokeMethod invoke, ProgramMethod context);

  boolean passesInliningConstraints(
      SingleResolutionResult<?> resolutionResult,
      ProgramMethod candidate,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter);

  InlineResult computeInlining(
      IRCode code,
      InvokeMethod invoke,
      SingleResolutionResult<?> resolutionResult,
      ProgramMethod singleTarget,
      ProgramMethod context,
      ClassInitializationAnalysis classInitializationAnalysis,
      InliningIRProvider inliningIRProvider,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter);
}
