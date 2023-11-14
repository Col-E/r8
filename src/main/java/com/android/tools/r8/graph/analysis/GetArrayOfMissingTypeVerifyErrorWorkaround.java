// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.FieldResolutionResult.SingleFieldResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.EnqueuerWorklist;
import com.android.tools.r8.shaking.KeepInfo.Joiner;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;

/**
 * In Dalvik it is a verification error to read and use a field of type Missing[].
 *
 * <p>Example:
 *
 * <pre>
 *   Consumer<?>[] consumer = this.field;
 *   acceptConsumer(consumer); // acceptConsumer(Consumer[])
 * </pre>
 *
 * <p>To avoid that the compiler moves such code into other contexts (e.g., as a result of inlining
 * or class merging), and thereby causes new classes to no longer verify on Dalvik, we soft-pin
 * methods that reads such fields.
 */
public class GetArrayOfMissingTypeVerifyErrorWorkaround implements EnqueuerFieldAccessAnalysis {

  private final DexItemFactory dexItemFactory;
  private final Enqueuer enqueuer;
  private final AndroidApiLevelCompute apiLevelCompute;

  public GetArrayOfMissingTypeVerifyErrorWorkaround(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    this.dexItemFactory = appView.dexItemFactory();
    this.enqueuer = enqueuer;
    this.apiLevelCompute = appView.apiLevelCompute();
  }

  public static void register(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    if (!isNoop(appView)) {
      enqueuer.registerFieldAccessAnalysis(
          new GetArrayOfMissingTypeVerifyErrorWorkaround(appView, enqueuer));
    }
  }

  private static boolean isNoop(AppView<? extends AppInfoWithClassHierarchy> appView) {
    InternalOptions options = appView.options();
    return options.isGeneratingDex()
        && options.getMinApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L);
  }

  @Override
  public void traceInstanceFieldRead(
      DexField field,
      FieldResolutionResult resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist) {
    if (isUnsafeToUseFieldOnDalvik(field)) {
      enqueuer.getKeepInfo().joinMethod(context, Joiner::disallowOptimization);
    }
  }

  @Override
  public void traceStaticFieldRead(
      DexField field,
      SingleFieldResolutionResult<?> resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist) {
    if (isUnsafeToUseFieldOnDalvik(field)) {
      enqueuer.getKeepInfo().joinMethod(context, Joiner::disallowOptimization);
    }
  }

  private boolean isUnsafeToUseFieldOnDalvik(DexField field) {
    DexType fieldType = field.getType();
    if (!fieldType.isArrayType()) {
      return false;
    }
    DexType baseType = fieldType.toBaseType(dexItemFactory);
    if (!baseType.isClassType()) {
      return false;
    }
    ComputedApiLevel baseTypeApiLevel =
        apiLevelCompute.computeApiLevelForLibraryReference(baseType, ComputedApiLevel.unknown());
    return !baseTypeApiLevel.isKnownApiLevel()
        || baseTypeApiLevel
            .asKnownApiLevel()
            .getApiLevel()
            .isGreaterThanOrEqualTo(AndroidApiLevel.L);
  }

  @Override
  public void traceInstanceFieldWrite(
      DexField field,
      FieldResolutionResult resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist) {
    // Intentionally empty.
  }

  @Override
  public void traceStaticFieldWrite(
      DexField field,
      FieldResolutionResult resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist) {
    // Intentionally empty.
  }
}
