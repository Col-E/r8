// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.MethodProcessingId;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.templates.CfUtilityMethodsForCodeOptimizations;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.InternalOptions;

public class UtilityMethodsForCodeOptimizations {

  public static UtilityMethodForCodeOptimizations synthesizeThrowClassCastExceptionIfNotNullMethod(
      AppView<?> appView, ProgramMethod context, MethodProcessingId methodProcessingId) {
    InternalOptions options = appView.options();
    if (options.isGeneratingClassFiles()) {
      // TODO(b/172194277): Allow synthetics when generating CF.
      return null;
    }
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexProto proto = dexItemFactory.createProto(dexItemFactory.voidType, dexItemFactory.objectType);
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    ProgramMethod syntheticMethod =
        syntheticItems.createMethod(
            context,
            dexItemFactory,
            builder ->
                builder
                    .setProto(proto)
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setCode(
                        method -> getThrowClassCastExceptionIfNotNullCodeTemplate(method, options)),
            methodProcessingId);
    return new UtilityMethodForCodeOptimizations(syntheticMethod);
  }

  private static CfCode getThrowClassCastExceptionIfNotNullCodeTemplate(
      DexMethod method, InternalOptions options) {
    return CfUtilityMethodsForCodeOptimizations
        .CfUtilityMethodsForCodeOptimizationsTemplates_throwClassCastExceptionIfNotNull(
            options, method);
  }

  public static class UtilityMethodForCodeOptimizations {

    private final ProgramMethod method;
    private boolean optimized;

    private UtilityMethodForCodeOptimizations(ProgramMethod method) {
      this.method = method;
    }

    public ProgramMethod getMethod() {
      assert optimized;
      return method;
    }

    public void optimize(MethodProcessor methodProcessor) {
      methodProcessor.scheduleMethodForProcessingAfterCurrentWave(method);
      optimized = true;
    }
  }
}
