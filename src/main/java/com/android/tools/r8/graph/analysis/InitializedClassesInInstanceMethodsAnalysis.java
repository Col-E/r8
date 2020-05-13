// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.shaking.Enqueuer;
import java.util.IdentityHashMap;
import java.util.Map;

public class InitializedClassesInInstanceMethodsAnalysis extends EnqueuerAnalysis {

  // A simple structure that stores the result of the analysis.
  public static class InitializedClassesInInstanceMethods {

    private final AppView<? extends AppInfoWithClassHierarchy> appView;
    private final Map<DexType, DexType> mapping;

    private InitializedClassesInInstanceMethods(
        AppView<? extends AppInfoWithClassHierarchy> appView, Map<DexType, DexType> mapping) {
      this.appView = appView;
      this.mapping = mapping;
    }

    public boolean isClassDefinitelyLoadedInInstanceMethod(
        DexProgramClass subject, ProgramMethod context) {
      assert !context.getDefinition().isStatic();
      // If `subject` is kept, then it is instantiated by reflection, which means that the analysis
      // has not seen all allocation sites. In that case, we conservatively return false.
      AppInfoWithClassHierarchy appInfo = appView.appInfo();
      if (appInfo.hasLiveness() && appInfo.withLiveness().isPinned(subject.type)) {
        return false;
      }

      // Check that `subject` is guaranteed to be initialized in all instance methods of `context`.
      DexType guaranteedToBeInitializedInContext =
          mapping.getOrDefault(context.getHolderType(), appView.dexItemFactory().objectType);
      if (!appInfo.isSubtype(guaranteedToBeInitializedInContext, subject.type)) {
        return false;
      }

      // Also check that `subject` is not an interface, since interfaces are not initialized
      // transitively.
      return !subject.isInterface();
    }
  }

  private final AppView<? extends AppInfoWithClassHierarchy> appView;

  // If the mapping contains an entry `X -> Y`, then the type Y is guaranteed to be initialized in
  // all instance methods of X.
  private final Map<DexType, DexType> mapping = new IdentityHashMap<>();

  public InitializedClassesInInstanceMethodsAnalysis(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
  }

  @Override
  public void processNewlyInstantiatedClass(DexProgramClass clazz, ProgramMethod context) {
    DexType key = clazz.type;
    DexType objectType = appView.dexItemFactory().objectType;
    if (context == null) {
      // Record that we don't know anything about the set of classes that are guaranteed to be
      // initialized in the instance methods of `clazz`.
      mapping.put(key, objectType);
      return;
    }

    // Record that the enclosing class is guaranteed to be initialized at the allocation site.
    AppInfoWithClassHierarchy appInfo = appView.appInfo();
    DexType guaranteedToBeInitialized = context.getHolderType();
    DexType existingGuaranteedToBeInitialized =
        mapping.getOrDefault(key, guaranteedToBeInitialized);
    mapping.put(
        key,
        ClassTypeElement.computeLeastUpperBoundOfClasses(
            appInfo, guaranteedToBeInitialized, existingGuaranteedToBeInitialized));
  }

  @Override
  public void done(Enqueuer enqueuer) {
    appView.setInitializedClassesInInstanceMethods(
        new InitializedClassesInInstanceMethods(appView, mapping));
  }
}
