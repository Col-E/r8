// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.KeepInfo.Joiner;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

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
  private final Set<DexType> knownToBePresentOnDalvik;

  public GetArrayOfMissingTypeVerifyErrorWorkaround(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    this.dexItemFactory = appView.dexItemFactory();
    this.enqueuer = enqueuer;
    this.knownToBePresentOnDalvik =
        ImmutableSet.<DexType>builder()
            .add(dexItemFactory.boxedBooleanType)
            .add(dexItemFactory.boxedByteType)
            .add(dexItemFactory.boxedCharType)
            .add(dexItemFactory.boxedDoubleType)
            .add(dexItemFactory.boxedFloatType)
            .add(dexItemFactory.boxedIntType)
            .add(dexItemFactory.boxedLongType)
            .add(dexItemFactory.boxedShortType)
            .add(dexItemFactory.classType)
            .add(dexItemFactory.objectType)
            .add(dexItemFactory.enumType)
            .add(dexItemFactory.stringType)
            .build();
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
      DexField field, FieldResolutionResult resolutionResult, ProgramMethod context) {
    if (isUnsafeToUseFieldOnDalvik(field, context)) {
      enqueuer.getKeepInfo().joinMethod(context, Joiner::disallowOptimization);
    }
  }

  @Override
  public void traceStaticFieldRead(
      DexField field, FieldResolutionResult resolutionResult, ProgramMethod context) {
    if (isUnsafeToUseFieldOnDalvik(field, context)) {
      enqueuer.getKeepInfo().joinMethod(context, Joiner::disallowOptimization);
    }
  }

  private boolean isUnsafeToUseFieldOnDalvik(DexField field, ProgramMethod context) {
    DexType fieldType = field.getType();
    if (!fieldType.isArrayType()) {
      return false;
    }
    DexType baseType = fieldType.toBaseType(dexItemFactory);
    if (!baseType.isClassType()) {
      return false;
    }
    if (knownToBePresentOnDalvik.contains(baseType)) {
      return false;
    }
    // TODO(b/206891715): Use the API database to determine if the given type is introduced in API
    //  level L or later.
    DexClass baseClass = enqueuer.definitionFor(baseType, context);
    return baseClass != null && baseClass.isLibraryClass();
  }

  @Override
  public void traceInstanceFieldWrite(
      DexField field, FieldResolutionResult resolutionResult, ProgramMethod context) {
    // Intentionally empty.
  }

  @Override
  public void traceStaticFieldWrite(
      DexField field, FieldResolutionResult resolutionResult, ProgramMethod context) {
    // Intentionally empty.
  }
}
