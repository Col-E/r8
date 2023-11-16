// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static com.android.tools.r8.profile.art.ArtProfileCompletenessChecker.CompletenessExceptions.ALLOW_MISSING_ENUM_UNBOXING_UTILITY_METHODS;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Verifies that, if given an ART profile containing all program classes and methods in the input,
 * then the residual ART profile also contains all classes and methods in the output.
 *
 * <p>If this check fails, either:
 *
 * <ul>
 *   <li>The current change added new synthetics to the program that need to be added to each
 *       profile that contains the synthesizing context, or
 *   <li>The existing rewriting of ART profiles and inclusion of synthetics in incomplete.
 * </ul>
 *
 * <p>In the latter case, create a tracking bug and suppress the assertion failure by setting {@link
 * ArtProfileOptions#setEnableCompletenessCheckForTesting(boolean)} to false.
 */
public class ArtProfileCompletenessChecker {

  public enum CompletenessExceptions {
    ALLOW_MISSING_ENUM_UNBOXING_UTILITY_METHODS
  }

  public static boolean verify(
      AppView<?> appView, CompletenessExceptions... completenessExceptions) {
    if (appView.options().getArtProfileOptions().isCompletenessCheckForTestingEnabled()) {
      ArtProfile completeArtProfile = appView.getArtProfileCollection().asNonEmpty().getLast();
      assert verifyProfileIsComplete(
          appView, completeArtProfile, Sets.newHashSet(completenessExceptions));
    }
    return true;
  }

  private static boolean verifyProfileIsComplete(
      AppView<?> appView,
      ArtProfile artProfile,
      Set<CompletenessExceptions> completenessExceptions) {
    assert !appView.getSyntheticItems().hasPendingSyntheticClasses();
    List<DexReference> missing = new ArrayList<>();
    for (DexProgramClass clazz : appView.appInfo().classesWithDeterministicOrder()) {
      if (appView.horizontallyMergedClasses().hasBeenMergedIntoDifferentType(clazz.getType())
          || (appView.hasVerticallyMergedClasses()
              && appView.getVerticallyMergedClasses().hasBeenMergedIntoSubtype(clazz.getType()))
          || appView.unboxedEnums().isUnboxedEnum(clazz)) {
        continue;
      }
      if (!artProfile.containsClassRule(clazz.getType())) {
        recordMissingDefinition(appView, clazz, completenessExceptions, missing);
      }
      for (ProgramMethod method : clazz.programMethods()) {
        if (!artProfile.containsMethodRule(method.getReference())) {
          recordMissingDefinition(appView, method, completenessExceptions, missing);
        }
      }
    }
    if (!missing.isEmpty()) {
      String message =
          StringUtils.join(System.lineSeparator(), missing, DexReference::toSmaliString);
      assert false : message;
    }
    return true;
  }

  private static void recordMissingDefinition(
      AppView<?> appView,
      ProgramDefinition definition,
      Set<CompletenessExceptions> completenessExceptions,
      List<DexReference> missing) {
    if (completenessExceptions.contains(ALLOW_MISSING_ENUM_UNBOXING_UTILITY_METHODS)) {
      DexType contextType = definition.getContextType();
      SyntheticItems syntheticItems = appView.getSyntheticItems();
      if (syntheticItems.isSynthetic(contextType)) {
        if (syntheticItems.isSyntheticOfKind(
                contextType, naming -> naming.ENUM_UNBOXING_CHECK_NOT_ZERO_METHOD)
            || syntheticItems.isSyntheticOfKind(
                contextType, naming -> naming.ENUM_UNBOXING_LOCAL_UTILITY_CLASS)
            || syntheticItems.isSyntheticOfKind(
                contextType, naming -> naming.ENUM_UNBOXING_SHARED_UTILITY_CLASS)) {
          return;
        }
      }
    }
    missing.add(definition.getReference());
  }
}
