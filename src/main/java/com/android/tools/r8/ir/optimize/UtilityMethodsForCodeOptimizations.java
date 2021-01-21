// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.cf.CfVersion;
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
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.utils.InternalOptions;

public class UtilityMethodsForCodeOptimizations {

  public static UtilityMethodForCodeOptimizations synthesizeToStringIfNotNullMethod(
      AppView<?> appView, ProgramMethod context, MethodProcessingId methodProcessingId) {
    InternalOptions options = appView.options();
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexProto proto = dexItemFactory.createProto(dexItemFactory.voidType, dexItemFactory.objectType);
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    ProgramMethod syntheticMethod =
        syntheticItems.createMethod(
            SyntheticNaming.SyntheticKind.TO_STRING_IF_NOT_NULL,
            context,
            dexItemFactory,
            builder ->
                builder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setClassFileVersion(CfVersion.V1_8)
                    .setCode(method -> getToStringIfNotNullCodeTemplate(method, options))
                    .setProto(proto),
            methodProcessingId);
    return new UtilityMethodForCodeOptimizations(syntheticMethod);
  }

  private static CfCode getToStringIfNotNullCodeTemplate(
      DexMethod method, InternalOptions options) {
    return CfUtilityMethodsForCodeOptimizations
        .CfUtilityMethodsForCodeOptimizationsTemplates_toStringIfNotNull(options, method);
  }

  public static UtilityMethodForCodeOptimizations synthesizeThrowClassCastExceptionIfNotNullMethod(
      AppView<?> appView, ProgramMethod context, MethodProcessingId methodProcessingId) {
    InternalOptions options = appView.options();
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexProto proto = dexItemFactory.createProto(dexItemFactory.voidType, dexItemFactory.objectType);
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    ProgramMethod syntheticMethod =
        syntheticItems.createMethod(
            SyntheticNaming.SyntheticKind.THROW_CCE_IF_NOT_NULL,
            context,
            dexItemFactory,
            builder ->
                builder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setClassFileVersion(CfVersion.V1_8)
                    .setCode(
                        method -> getThrowClassCastExceptionIfNotNullCodeTemplate(method, options))
                    .setProto(proto),
            methodProcessingId);
    return new UtilityMethodForCodeOptimizations(syntheticMethod);
  }

  private static CfCode getThrowClassCastExceptionIfNotNullCodeTemplate(
      DexMethod method, InternalOptions options) {
    return CfUtilityMethodsForCodeOptimizations
        .CfUtilityMethodsForCodeOptimizationsTemplates_throwClassCastExceptionIfNotNull(
            options, method);
  }

  public static UtilityMethodForCodeOptimizations synthesizeThrowIncompatibleClassChangeErrorMethod(
      AppView<?> appView, ProgramMethod context, MethodProcessingId methodProcessingId) {
    InternalOptions options = appView.options();
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexProto proto = dexItemFactory.createProto(dexItemFactory.icceType);
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    ProgramMethod syntheticMethod =
        syntheticItems.createMethod(
            SyntheticNaming.SyntheticKind.THROW_ICCE,
            context,
            dexItemFactory,
            builder ->
                builder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setClassFileVersion(CfVersion.V1_8)
                    .setCode(
                        method -> getThrowIncompatibleClassChangeErrorCodeTemplate(method, options))
                    .setProto(proto),
            methodProcessingId);
    return new UtilityMethodForCodeOptimizations(syntheticMethod);
  }

  private static CfCode getThrowIncompatibleClassChangeErrorCodeTemplate(
      DexMethod method, InternalOptions options) {
    return CfUtilityMethodsForCodeOptimizations
        .CfUtilityMethodsForCodeOptimizationsTemplates_throwIncompatibleClassChangeError(
            options, method);
  }

  public static UtilityMethodForCodeOptimizations synthesizeThrowNoSuchMethodErrorMethod(
      AppView<?> appView, ProgramMethod context, MethodProcessingId methodProcessingId) {
    InternalOptions options = appView.options();
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexProto proto = dexItemFactory.createProto(dexItemFactory.noSuchMethodErrorType);
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    ProgramMethod syntheticMethod =
        syntheticItems.createMethod(
            SyntheticNaming.SyntheticKind.THROW_NSME,
            context,
            dexItemFactory,
            builder ->
                builder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setClassFileVersion(CfVersion.V1_8)
                    .setCode(method -> getThrowNoSuchMethodErrorCodeTemplate(method, options))
                    .setProto(proto),
            methodProcessingId);
    return new UtilityMethodForCodeOptimizations(syntheticMethod);
  }

  private static CfCode getThrowNoSuchMethodErrorCodeTemplate(
      DexMethod method, InternalOptions options) {
    return CfUtilityMethodsForCodeOptimizations
        .CfUtilityMethodsForCodeOptimizationsTemplates_throwNoSuchMethodError(options, method);
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
