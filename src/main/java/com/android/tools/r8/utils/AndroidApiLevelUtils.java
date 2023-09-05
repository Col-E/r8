// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static com.android.tools.r8.graph.DexLibraryClass.asLibraryClassOrNull;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.LibraryClass;
import com.android.tools.r8.graph.LibraryDefinition;
import com.android.tools.r8.graph.LibraryMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.inliner.NopWhyAreYouNotInliningReporter;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Set;

public class AndroidApiLevelUtils {

  public static boolean isApiSafeForInlining(
      ProgramMethod caller, ProgramMethod inlinee, InternalOptions options) {
    return isApiSafeForInlining(
        caller, inlinee, options, NopWhyAreYouNotInliningReporter.getInstance());
  }

  @SuppressWarnings("ReferenceEquality")
  public static boolean isApiSafeForInlining(
      ProgramMethod caller,
      ProgramMethod inlinee,
      InternalOptions options,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    if (!options.apiModelingOptions().isApiCallerIdentificationEnabled()) {
      return true;
    }
    if (caller.getHolderType() == inlinee.getHolderType()) {
      return true;
    }
    ComputedApiLevel callerApiLevelForCode = caller.getDefinition().getApiLevelForCode();
    if (callerApiLevelForCode.isUnknownApiLevel()) {
      whyAreYouNotInliningReporter.reportCallerHasUnknownApiLevel();
      return false;
    }
    // For inlining we only measure if the code has invokes into the library.
    ComputedApiLevel inlineeApiLevelForCode = inlinee.getDefinition().getApiLevelForCode();
    if (!caller
        .getDefinition()
        .getApiLevelForCode()
        .isGreaterThanOrEqualTo(inlineeApiLevelForCode)) {
      whyAreYouNotInliningReporter.reportInlineeHigherApiCall(
          callerApiLevelForCode, inlineeApiLevelForCode);
      return false;
    }
    return true;
  }

  public static ComputedApiLevel getApiReferenceLevelForMerging(
      AndroidApiLevelCompute apiLevelCompute, DexProgramClass clazz) {
    // The api level of a class is the max level of it's members, super class and interfaces.
    return getMembersApiReferenceLevelForMerging(
        clazz, apiLevelCompute.computeApiLevelForDefinition(clazz.allImmediateSupertypes()));
  }

  public static ComputedApiLevel getMembersApiReferenceLevelForMerging(
      DexProgramClass clazz, ComputedApiLevel memberLevel) {
    // Based on b/138781768#comment57 there is almost no penalty for having an unknown reference
    // as long as we are not invoking or accessing a field on it. Therefore we can disregard static
    // types of fields and only consider method code api levels.
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.hasCode()) {
        ComputedApiLevel apiLevelForCode = method.getApiLevelForCode();
        if (apiLevelForCode.isNotSetApiLevel()) {
          return ComputedApiLevel.notSet();
        }
        memberLevel = memberLevel.max(apiLevelForCode);
      }
      if (memberLevel.isUnknownApiLevel()) {
        return memberLevel;
      }
    }
    return memberLevel;
  }

  public static boolean isApiSafeForMemberRebinding(
      LibraryMethod method,
      DexMethod original,
      AndroidApiLevelCompute androidApiLevelCompute,
      InternalOptions options) {
    if (!androidApiLevelCompute.isEnabled()) {
      assert !options.apiModelingOptions().enableLibraryApiModeling;
      return false;
    }
    assert options.apiModelingOptions().enableLibraryApiModeling;
    ComputedApiLevel apiLevel =
        androidApiLevelCompute.computeApiLevelForLibraryReference(
            method.getReference(), ComputedApiLevel.unknown());
    if (apiLevel.isUnknownApiLevel()) {
      return false;
    }
    ComputedApiLevel apiLevelOfOriginal =
        androidApiLevelCompute.computeApiLevelForLibraryReference(
            original, ComputedApiLevel.unknown());
    if (apiLevelOfOriginal.isUnknownApiLevel()) {
      return false;
    }
    return apiLevelOfOriginal.max(apiLevel).isLessThanOrEqualTo(options.getMinApiLevel()).isTrue();
  }

  public static boolean isApiSafeForReference(LibraryDefinition definition, AppView<?> appView) {
    return isApiSafeForReference(
        definition, appView.apiLevelCompute(), appView.options(), appView.dexItemFactory());
  }

  @SuppressWarnings("ReferenceEquality")
  private static boolean isApiSafeForReference(
      LibraryDefinition definition,
      AndroidApiLevelCompute androidApiLevelCompute,
      InternalOptions options,
      DexItemFactory factory) {
    if (!options.apiModelingOptions().isApiLibraryModelingEnabled()) {
      return factory.libraryTypesAssumedToBePresent.contains(definition.getContextType());
    }
    ComputedApiLevel apiLevel =
        androidApiLevelCompute.computeApiLevelForLibraryReference(
            definition.getReference(), ComputedApiLevel.unknown());
    return apiLevel.isLessThanOrEqualTo(options.getMinApiLevel()).isTrue();
  }

  private static boolean isApiSafeForReference(
      LibraryDefinition newDefinition, LibraryDefinition oldDefinition, AppView<?> appView) {
    assert appView.options().apiModelingOptions().isApiLibraryModelingEnabled();
    assert !isApiSafeForReference(newDefinition, appView)
        : "Clients should first check if the definition is present on all apis since the min api";
    AndroidApiLevelCompute androidApiLevelCompute = appView.apiLevelCompute();
    ComputedApiLevel apiLevel =
        androidApiLevelCompute.computeApiLevelForLibraryReference(
            newDefinition.getReference(), ComputedApiLevel.unknown());
    if (apiLevel.isUnknownApiLevel()) {
      return false;
    }
    ComputedApiLevel apiLevelOfOriginal =
        androidApiLevelCompute.computeApiLevelForLibraryReference(
            oldDefinition.getReference(), ComputedApiLevel.unknown());
    return apiLevel.isLessThanOrEqualTo(apiLevelOfOriginal).isTrue();
  }

  @SuppressWarnings("ReferenceEquality")
  public static boolean isApiSafeForTypeStrengthening(
      DexType newType, DexType oldType, AppView<? extends AppInfoWithClassHierarchy> appView) {
    // Type strengthening only applies to reference types.
    assert newType.isReferenceType();
    assert oldType.isReferenceType();
    assert newType != oldType;
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexType newBaseType = newType.toBaseType(dexItemFactory);
    if (newBaseType.isPrimitiveType()) {
      // Array of primitives is available on all api levels.
      return true;
    }
    assert newBaseType.isClassType();
    DexClass newBaseClass = appView.definitionFor(newBaseType);
    if (newBaseClass == null) {
      // This could be a library class that is only available on newer api levels.
      return false;
    }
    if (!newBaseClass.isLibraryClass()) {
      // Program and classpath classes are not api level dependent.
      return true;
    }
    if (!appView.options().apiModelingOptions().isApiLibraryModelingEnabled()) {
      // Conservatively bail out if we don't have api modeling.
      return false;
    }
    LibraryClass newBaseLibraryClass = newBaseClass.asLibraryClass();
    if (isApiSafeForReference(newBaseLibraryClass, appView)) {
      // Library class is present on all api levels since min api.
      return true;
    }
    // Check if the new library class is present since the api level of the old type.
    DexType oldBaseType = oldType.toBaseType(dexItemFactory);
    assert oldBaseType.isClassType();
    LibraryClass oldBaseLibraryClass = asLibraryClassOrNull(appView.definitionFor(oldBaseType));
    return oldBaseLibraryClass != null
        && isApiSafeForReference(newBaseLibraryClass, oldBaseLibraryClass, appView);
  }

  public static Pair<DexClass, ComputedApiLevel> findAndComputeApiLevelForLibraryDefinition(
      AppView<?> appView,
      AppInfoWithClassHierarchy appInfo,
      DexClass holder,
      DexMember<?, ?> reference) {
    AndroidApiLevelCompute apiLevelCompute = appView.apiLevelCompute();
    if (holder.isLibraryClass()) {
      return Pair.create(
          holder,
          apiLevelCompute.computeApiLevelForLibraryReference(
              reference, ComputedApiLevel.unknown()));
    }
    // The API database do not allow for resolving into it (since that is not stable), and it is
    // therefore designed in a way where all members of classes can be queried on any sub-type with
    // the api level for where it is reachable. It is therefore sufficient for us, to figure out if
    // an instruction is a library call, to either find a program definition or to find the library
    // frontier.
    // Scan through the type hierarchy to find the first library class or program definition.
    DexClass firstClassWithReferenceOrLibraryClass =
        firstLibraryClassOrProgramClassWithDefinition(appInfo, holder, reference);
    if (firstClassWithReferenceOrLibraryClass == null) {
      return Pair.create(null, ComputedApiLevel.unknown());
    }
    if (!firstClassWithReferenceOrLibraryClass.isLibraryClass()) {
      return Pair.create(firstClassWithReferenceOrLibraryClass, appView.computedMinApiLevel());
    }
    ComputedApiLevel apiLevel =
        apiLevelCompute.computeApiLevelForLibraryReference(
            reference.withHolder(
                firstClassWithReferenceOrLibraryClass.getType(), appView.dexItemFactory()),
            ComputedApiLevel.unknown());
    if (apiLevel.isKnownApiLevel()) {
      return Pair.create(firstClassWithReferenceOrLibraryClass, apiLevel);
    }
    // We were unable to find a definition in the class hierarchy, check all interfaces for a
    // definition or the library interfaces for the first interface definition.
    Set<DexClass> firstLibraryInterfaces =
        findAllFirstLibraryInterfacesOrProgramClassWithDefinition(appInfo, holder, reference);
    if (firstLibraryInterfaces.size() == 1) {
      DexClass firstClass = firstLibraryInterfaces.iterator().next();
      if (!firstClass.isLibraryClass()) {
        return Pair.create(firstClass, appView.computedMinApiLevel());
      }
    }
    DexClass foundClass = null;
    ComputedApiLevel minApiLevel = ComputedApiLevel.unknown();
    for (DexClass libraryInterface : firstLibraryInterfaces) {
      assert libraryInterface.isLibraryClass();
      ComputedApiLevel libraryIfaceApiLevel =
          apiLevelCompute.computeApiLevelForLibraryReference(
              reference.withHolder(
                  firstClassWithReferenceOrLibraryClass.getType(), appView.dexItemFactory()),
              ComputedApiLevel.unknown());
      if (minApiLevel.isGreaterThan(libraryIfaceApiLevel)) {
        minApiLevel = libraryIfaceApiLevel;
        foundClass = libraryInterface;
      }
    }
    return Pair.create(foundClass, minApiLevel);
  }

  private static DexClass firstLibraryClassOrProgramClassWithDefinition(
      AppInfoWithClassHierarchy appInfo, DexClass originalClass, DexMember<?, ?> reference) {
    if (originalClass.isLibraryClass()) {
      return originalClass;
    }
    return WorkList.newIdentityWorkList(originalClass)
        .run(
            (clazz, workList) -> {
              if (clazz.isLibraryClass()) {
                return TraversalContinuation.doBreak(clazz);
              } else if (clazz.lookupMember(reference) != null) {
                return TraversalContinuation.doBreak(clazz);
              } else if (clazz.getSuperType() != null) {
                appInfo
                    .contextIndependentDefinitionForWithResolutionResult(clazz.getSuperType())
                    .forEachClassResolutionResult(workList::addIfNotSeen);
              }
              return TraversalContinuation.doContinue();
            })
        .asBreakOrDefault(null)
        .getValue();
  }

  private static Set<DexClass> findAllFirstLibraryInterfacesOrProgramClassWithDefinition(
      AppInfoWithClassHierarchy appInfo, DexClass originalClass, DexMember<?, ?> reference) {
    Set<DexClass> interfaces = Sets.newLinkedHashSet();
    WorkList<DexClass> workList = WorkList.newIdentityWorkList(originalClass);
    while (workList.hasNext()) {
      DexClass clazz = workList.next();
      if (clazz.isLibraryClass()) {
        if (clazz.isInterface()) {
          interfaces.add(clazz);
        }
      } else if (clazz.lookupMember(reference) != null) {
        return Collections.singleton(clazz);
      } else {
        clazz.forEachImmediateSupertype(
            superType ->
                appInfo
                    .contextIndependentDefinitionForWithResolutionResult(superType)
                    .forEachClassResolutionResult(workList::addIfNotSeen));
      }
    }
    return interfaces;
  }

  /**
   * A lot of functionality has already been outlined in androidx. The ordinary pattern for manual
   * outlining is to create a class with the name ApiXXImpl where XX is the api level. This method
   * will check the context to see if it matches this pattern in androidx and extract the api level
   * for comparison with the computed api level.
   */
  public static boolean isOutlinedAtSameOrLowerLevel(
      DexProgramClass clazz, ComputedApiLevel apiLevel) {
    assert apiLevel.isKnownApiLevel();
    if (!clazz.getType().getDescriptor().startsWith("Landroidx/")) {
      return false;
    }
    String simpleName = clazz.getSimpleName();
    int apiIndex = simpleName.indexOf("Api");
    if (apiIndex < 0) {
      return false;
    }
    int endApiIndex = apiIndex += 3;
    int implIndex = simpleName.indexOf("Impl");
    if (implIndex < 0 || implIndex < endApiIndex || (implIndex - endApiIndex) != 2) {
      return false;
    }
    String apiLevelAsString = simpleName.substring(endApiIndex, implIndex);
    if (!StringUtils.onlyContainsDigits(apiLevelAsString)) {
      return false;
    }
    int apiLevelAsInt = Integer.parseInt(apiLevelAsString);
    if (apiLevelAsInt < 10 || apiLevelAsInt > AndroidApiLevel.LATEST.getLevel()) {
      return false;
    }
    return apiLevel.asKnownApiLevel().getApiLevel().getLevel() <= apiLevelAsInt;
  }

  public static boolean isApiLevelLessThanOrEqualToG(ComputedApiLevel apiLevel) {
    return apiLevel.isKnownApiLevel()
        && apiLevel.asKnownApiLevel().getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.G);
  }
}
