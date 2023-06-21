// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.StaticFieldValues;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.PostMethodProcessor.Builder;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public abstract class EnumUnboxer {

  public static EnumUnboxer create(AppView<AppInfoWithLiveness> appView) {
    return appView.options().enableEnumUnboxing ? new EnumUnboxerImpl(appView) : empty();
  }

  public static EmptyEnumUnboxer empty() {
    return EmptyEnumUnboxer.get();
  }

  public abstract void prepareForPrimaryOptimizationPass(
      GraphLens graphLensForPrimaryOptimizationPass);

  public abstract void analyzeEnums(IRCode code, MethodProcessor methodProcessor);

  public abstract void onMethodPruned(ProgramMethod method);

  public abstract void onMethodCodePruned(ProgramMethod method);

  public abstract void recordEnumState(DexProgramClass clazz, StaticFieldValues staticFieldValues);

  public abstract Set<Phi> rewriteCode(
      IRCode code, MethodProcessor methodProcessor, RewrittenPrototypeDescription prototypeChanges);

  public abstract void rewriteWithLens();

  public abstract void unboxEnums(
      AppView<AppInfoWithLiveness> appView,
      IRConverter converter,
      Builder postMethodProcessorBuilder,
      ExecutorService executorService,
      OptimizationFeedbackDelayed feedback,
      Timing timing)
      throws ExecutionException;

  public abstract void unsetRewriter();

  public abstract void updateEnumUnboxingCandidatesInfo();
}
