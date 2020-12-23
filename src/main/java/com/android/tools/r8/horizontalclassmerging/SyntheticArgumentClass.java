// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.MainDexTracingResult;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lets assume we are merging a class A that looks like:
 *
 * <pre>
 *   class A {
 *     A() { ... }
 *     A(int) { ... }
 *   }
 * </pre>
 *
 * If {@code A::<init>()} is merged with other constructors, then we need to prevent a conflict with
 * {@code A::<init>(int)}. To do this we introduce a synthetic class so that the new signature for
 * the merged constructor is {@code A::<init>(SyntheticClass, int)}, as this is guaranteed to be
 * unique.
 *
 * <p>This class generates a synthetic class in the package of the first class to be merged.
 */
public class SyntheticArgumentClass {
  public static final String SYNTHETIC_CLASS_SUFFIX = "$r8$HorizontalClassMergingArgument";

  private final List<DexType> syntheticClassTypes;

  private SyntheticArgumentClass(List<DexType> syntheticClassTypes) {
    this.syntheticClassTypes = syntheticClassTypes;
  }

  public List<DexType> getArgumentClasses() {
    return syntheticClassTypes;
  }

  public static class Builder {

    private final DirectMappedDexApplication.Builder appBuilder;
    private final AppView<AppInfoWithLiveness> appView;
    private final MainDexTracingResult mainDexTracingResult;
    private final MainDexTracingResult.Builder mainDexTracingResultBuilder;

    Builder(
        DirectMappedDexApplication.Builder appBuilder,
        AppView<AppInfoWithLiveness> appView,
        MainDexTracingResult mainDexTracingResult,
        MainDexTracingResult.Builder mainDexTracingResultBuilder) {
      this.appBuilder = appBuilder;
      this.appView = appView;
      this.mainDexTracingResult = mainDexTracingResult;
      this.mainDexTracingResultBuilder = mainDexTracingResultBuilder;
    }

    private DexType synthesizeClass(
        DexProgramClass context,
        boolean requiresMainDex,
        boolean addToMainDexTracingResult,
        int index) {
      DexType syntheticClassType =
          appView
              .dexItemFactory()
              .createFreshTypeName(
                  context.type.addSuffix(SYNTHETIC_CLASS_SUFFIX, appView.dexItemFactory()),
                  type -> appView.appInfo().definitionForWithoutExistenceAssert(type) == null,
                  index);

      DexProgramClass clazz =
          new DexProgramClass(
              syntheticClassType,
              null,
              new SynthesizedOrigin("horizontal class merging", HorizontalClassMerger.class),
              ClassAccessFlags.fromCfAccessFlags(Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC),
              appView.dexItemFactory().objectType,
              DexTypeList.empty(),
              null,
              null,
              Collections.emptyList(),
              null,
              Collections.emptyList(),
              ClassSignature.noSignature(),
              DexAnnotationSet.empty(),
              DexEncodedField.EMPTY_ARRAY,
              DexEncodedField.EMPTY_ARRAY,
              DexEncodedMethod.EMPTY_ARRAY,
              DexEncodedMethod.EMPTY_ARRAY,
              appView.dexItemFactory().getSkipNameValidationForTesting(),
              DexProgramClass::checksumFromType);

      appBuilder.addSynthesizedClass(clazz);
      appView.appInfo().addSynthesizedClass(clazz, requiresMainDex);
      if (addToMainDexTracingResult) {
        mainDexTracingResultBuilder.addRoot(clazz);
      }

      return clazz.type;
    }

    public SyntheticArgumentClass build(Iterable<DexProgramClass> mergeClasses) {
      // Find a fresh name in an existing package.
      DexProgramClass context = mergeClasses.iterator().next();

      // Add to the main dex list if one of the merged classes is in the main dex.
      boolean requiresMainDex = appView.appInfo().getMainDexClasses().containsAnyOf(mergeClasses);

      // Also add as a root to the main dex tracing result if any of the merged classes is a root.
      // This is needed to satisfy an assertion in the inliner that verifies that we do not inline
      // methods with references to non-roots into classes that are roots.
      boolean addToMainDexTracingResult = Iterables.any(mergeClasses, mainDexTracingResult::isRoot);

      List<DexType> syntheticArgumentTypes = new ArrayList<>();
      for (int i = 0;
          i < appView.options().horizontalClassMergerOptions().getSyntheticArgumentCount();
          i++) {
        syntheticArgumentTypes.add(
            synthesizeClass(context, requiresMainDex, addToMainDexTracingResult, i));
      }

      return new SyntheticArgumentClass(syntheticArgumentTypes);
    }
  }
}
