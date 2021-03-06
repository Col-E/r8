// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import java.util.ArrayList;
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

    private final AppView<AppInfoWithLiveness> appView;

    Builder(AppView<AppInfoWithLiveness> appView) {
      this.appView = appView;
    }

    private DexProgramClass synthesizeClass(DexProgramClass context, SyntheticKind syntheticKind) {
      return appView
          .getSyntheticItems()
          .createFixedClass(syntheticKind, context, appView.dexItemFactory(), builder -> {});
    }

    public SyntheticArgumentClass build(Iterable<DexProgramClass> mergeClasses) {
      // Find a fresh name in an existing package.
      DexProgramClass context = mergeClasses.iterator().next();
      List<DexType> syntheticArgumentTypes = new ArrayList<>();
      syntheticArgumentTypes.add(
          synthesizeClass(context, SyntheticKind.HORIZONTAL_INIT_TYPE_ARGUMENT_1).getType());
      syntheticArgumentTypes.add(
          synthesizeClass(context, SyntheticKind.HORIZONTAL_INIT_TYPE_ARGUMENT_2).getType());
      syntheticArgumentTypes.add(
          synthesizeClass(context, SyntheticKind.HORIZONTAL_INIT_TYPE_ARGUMENT_3).getType());
      return new SyntheticArgumentClass(syntheticArgumentTypes);
    }
  }
}
