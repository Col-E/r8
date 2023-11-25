// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.AndroidApiLevelUtils.isApiLevelLessThanOrEqualToG;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Set;

/***
 * Some Dalvik and ART runtimes have problems with verification when it comes to computing subtype
 * relationship. Take the following code:
 * <pre>
 *     public LibraryClass callLibraryWithDirectReturn() {
 *       if (AndroidBuildVersion.SDK_INT < 31) {
 *         return null;
 *       } else {
 *         LibrarySub sub = Outline.create();
 *         return sub;
 *       }
 *     }
 * </pre>
 *
 * This can cause verification failures if LibraryClass is known but LibrarySub is unknown. It seems
 * like the problem is that the verifier assumes that it can compute the relationship. If we add
 * an instruction that causes soft-verification we remove the hard verification error:
 * <pre>
 *     public LibraryClass callLibraryWithDirectReturn() {
 *       if (AndroidBuildVersion.SDK_INT < 31) {
 *         return null;
 *       } else {
 *         LibrarySub sub = Outline.create();
 *         sub.foo();
 *         return sub;
 *       }
 *     }
 * </pre>
 *
 * The assumption here is that the verifier will figure out that it needs to run this by
 * interpreting and bails out.
 *
 * We fix the issue here, both for our outlines and for manual outlines, by putting in a check-cast
 * on the return value if a potential unknown library subtype flows to the return value.
 * <pre>
 *     public LibraryClass callLibraryWithDirectReturn() {
 *       if (AndroidBuildVersion.SDK_INT < 31) {
 *         return null;
 *       } else {
 *         LibrarySub sub = Outline.create();
 *         return (LibraryClass)sub;
 *       }
 *     }
 * </pre>
 *
 * See b/272725341 for more information.
 */
public class RemoveVerificationErrorForUnknownReturnedValues {

  private final AppView<?> appView;
  private final AndroidApiLevelCompute apiLevelCompute;
  private final SyntheticItems syntheticItems;

  public RemoveVerificationErrorForUnknownReturnedValues(AppView<?> appView) {
    this.appView = appView;
    this.apiLevelCompute = appView.apiLevelCompute();
    this.syntheticItems = appView.getSyntheticItems();
  }

  private AppInfoWithClassHierarchy getAppInfoWithClassHierarchy() {
    return appView.appInfoForDesugaring();
  }

  public void run(ProgramMethod context, IRCode code, Timing timing) {
    timing.begin("Compute and insert checkcast on return values");
    AppInfoWithClassHierarchy appInfoWithClassHierarchy = getAppInfoWithClassHierarchy();
    Set<Return> returnValuesNeedingCheckCast =
        getReturnsPotentiallyNeedingCheckCast(appInfoWithClassHierarchy, context, code);
    insertCheckCastForReturnValues(context, code, returnValuesNeedingCheckCast);
    timing.end();
  }

  @SuppressWarnings("ReferenceEquality")
  private Set<Return> getReturnsPotentiallyNeedingCheckCast(
      AppInfoWithClassHierarchy appInfo, ProgramMethod context, IRCode code) {
    if (syntheticItems.isSyntheticOfKind(context.getHolderType(), kinds -> kinds.API_MODEL_OUTLINE)
        || syntheticItems.isSyntheticOfKind(
            context.getHolderType(), kinds -> kinds.API_MODEL_OUTLINE_WITHOUT_GLOBAL_MERGING)) {
      return Collections.emptySet();
    }
    DexType returnType = context.getReturnType();
    if (!returnType.isClassType()) {
      return Collections.emptySet();
    }
    // Everything is assignable to object type and the verifier do not throw an error here.
    if (returnType == appView.dexItemFactory().objectType) {
      return Collections.emptySet();
    }
    DexClass returnTypeClass = appInfo.definitionFor(returnType);
    if (returnTypeClass == null || !returnTypeClass.isLibraryClass()) {
      return Collections.emptySet();
    }
    ComputedApiLevel computedReturnApiLevel =
        apiLevelCompute.computeApiLevelForLibraryReference(returnType, ComputedApiLevel.unknown());
    if (computedReturnApiLevel.isUnknownApiLevel()) {
      return Collections.emptySet();
    }
    Set<Value> seenSet = Sets.newIdentityHashSet();
    Set<Return> returnsOfInterest = Sets.newIdentityHashSet();
    code.computeNormalExitBlocks()
        .forEach(
            basicBlock -> {
              Return exit = basicBlock.exit().asReturn();
              Value aliasedReturnValue = exit.returnValue().getAliasedValue();
              if (shouldInsertCheckCastForValue(appInfo, returnType, aliasedReturnValue, seenSet)) {
                returnsOfInterest.add(exit);
              }
            });
    return returnsOfInterest;
  }

  private boolean shouldInsertCheckCastForValue(
      AppInfoWithClassHierarchy appInfo, DexType returnType, Value value, Set<Value> seenSet) {
    WorkList<Value> workList = WorkList.newIdentityWorkList(value, seenSet);
    while (workList.hasNext()) {
      Value next = workList.next();
      if (next.isPhi()) {
        workList.addIfNotSeen(next.asPhi().getOperands());
      }
      TypeElement type = next.getType();
      if (!type.isClassType()) {
        assert type.isNullType() || type.isArrayType();
        continue;
      }
      DexType returnValueType = type.asClassType().getClassType();
      DexClass returnValueClass = appInfo.definitionFor(returnValueType);
      if (returnValueClass == null || !returnValueClass.isLibraryClass()) {
        continue;
      }
      if (!appInfo.isStrictSubtypeOf(returnValueType, returnType)) {
        continue;
      }
      ComputedApiLevel computedValueApiLevel =
          apiLevelCompute.computeApiLevelForLibraryReference(
              returnValueType, ComputedApiLevel.unknown());
      // We could in principle also bail out if the computedValueApiLevel == computedReturnApiLevel,
      // however, if we stub the return type class we will introduce the error again. We do not know
      // at this point if we stub the returnTypeClass.
      ComputedApiLevel minApiLevel = appView.computedMinApiLevel();
      if (!computedValueApiLevel.isUnknownApiLevel()
          && !isApiLevelLessThanOrEqualToG(computedValueApiLevel)
          && computedValueApiLevel.isGreaterThan(minApiLevel)
          && isDalvikOrSubTypeIntroducedLaterThanAndroidR(minApiLevel, computedValueApiLevel)) {
        return true;
      }
    }
    return false;
  }

  // Dalvik and some new ART versions have a stricter verifier that do not allow type-checking
  // unknown return value types against a known return type.
  private boolean isDalvikOrSubTypeIntroducedLaterThanAndroidR(
      ComputedApiLevel minApiLevel, ComputedApiLevel subTypeApiLevel) {
    if (minApiLevel.isLessThanOrEqualTo(AndroidApiLevel.K_WATCH).isPossiblyTrue()) {
      return true;
    }
    return subTypeApiLevel.isGreaterThan(AndroidApiLevel.R).isPossiblyTrue();
  }

  private void insertCheckCastForReturnValues(
      ProgramMethod context, IRCode code, Set<Return> returnsNeedingCast) {
    if (returnsNeedingCast.isEmpty()) {
      return;
    }
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Return returnInstruction = iterator.next().asReturn();
      if (returnInstruction == null) {
        continue;
      }
      DexType returnType = context.getReturnType();
      Value returnValue = returnInstruction.returnValue();
      CheckCast checkCast =
          CheckCast.builder()
              .setObject(returnValue)
              .setFreshOutValue(
                  code, returnType.toTypeElement(appView, returnValue.getType().nullability()))
              .setCastType(returnType)
              .setPosition(returnInstruction.getPosition())
              .build();
      iterator.replaceCurrentInstruction(checkCast);
      iterator.add(
          Return.builder()
              .setPosition(returnInstruction.getPosition())
              .setReturnValue(checkCast.outValue())
              .build());
    }
  }
}
