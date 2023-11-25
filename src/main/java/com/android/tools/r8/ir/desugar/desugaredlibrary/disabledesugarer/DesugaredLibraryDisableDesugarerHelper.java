// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.disabledesugarer;

import static com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryAPIConverter.methodWithVivifiedTypeInSignature;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryAPIConverter.vivifiedTypeFor;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.MemberResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.SuccessfulMemberResolutionResult;

public class DesugaredLibraryDisableDesugarerHelper {

  private final AppView<?> appView;

  public DesugaredLibraryDisableDesugarerHelper(AppView<?> appView) {
    this.appView = appView;
  }

  static boolean shouldCreate(AppView<?> appView) {
    for (DexType multiDexType : appView.dexItemFactory().multiDexTypes) {
      if (appView.appInfo().definitionForWithoutExistenceAssert(multiDexType) != null) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("ReferenceEquality")
  DexMethod rewriteMethod(DexMethod method, boolean isInterface, ProgramMethod context) {
    DexType newHolder = rewriteType(method.getHolderType());
    DexMethod rewrittenMethod = methodWithVivifiedTypeInSignature(method, newHolder, appView);
    if (rewrittenMethod == method) {
      return null;
    }
    MethodResolutionResult methodResolutionResult =
        appView.appInfoForDesugaring().resolveMethodLegacy(method, isInterface);
    warnIfInvalidResolution(methodResolutionResult, method, context);
    return rewrittenMethod;
  }

  @SuppressWarnings("ReferenceEquality")
  DexField rewriteField(DexField field, ProgramDefinition context) {
    if (isRewrittenType(field.getHolderType())) {
      // This case never happens within the supported set of classes. We can support it if required.
      appView
          .options()
          .reporter
          .error("Cannot prevent the desugaring of " + field + " in " + context);
      return null;
    }
    DexType rewrittenFieldType = rewriteType(field.getType());
    if (rewrittenFieldType == field.getType()) {
      return null;
    }
    FieldResolutionResult fieldResolutionResult =
        appView.appInfoForDesugaring().resolveField(field);
    warnIfInvalidResolution(fieldResolutionResult, field, context);
    return field.withType(rewrittenFieldType, appView.dexItemFactory());
  }

  /**
   * All rewritings should apply within private members of multidex types or with library accesses,
   * else we are leaving escapes of non rewritten types in the program which will lead to runtime
   * errors. Note that this is conservative and we could allow more escapes if required.
   */
  private boolean isValidResolution(MemberResolutionResult<?, ?> resolutionResult) {
    if (resolutionResult == null || !resolutionResult.isSuccessfulMemberResolutionResult()) {
      return false;
    }
    SuccessfulMemberResolutionResult<?, ?> successfulResult =
        resolutionResult.asSuccessfulMemberResolutionResult();
    if (successfulResult.getResolvedHolder().isLibraryClass()) {
      return true;
    }
    return appView
            .dexItemFactory()
            .multiDexTypes
            .contains(successfulResult.getResolvedHolder().getType())
        && successfulResult.getResolvedMember().isPrivate();
  }

  private void warnIfInvalidResolution(
      MemberResolutionResult<?, ?> resolutionResult,
      DexMember<?, ?> member,
      ProgramDefinition context) {
    if (isValidResolution(resolutionResult)) {
      return;
    }
    appView
        .reporter()
        .warning(
            "Preventing the desugaring of "
                + member
                + " in "
                + context
                + " which could be an invalid escape into the program. ");
  }

  DexType rewriteType(DexType type) {
    if (isRewrittenType(type)) {
      return vivifiedTypeFor(type, appView);
    }
    return type;
  }

  boolean isRewrittenType(DexType type) {
    return appView.typeRewriter.hasRewrittenType(type, appView);
  }
}
