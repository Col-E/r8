// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.utils;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import java.util.Map;

public abstract class NonProgramMethodsCollection {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Map<DexClass, DexMethodSignatureSet> nonProgramMethods;

  NonProgramMethodsCollection(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Map<DexClass, DexMethodSignatureSet> nonProgramMethods) {
    this.appView = appView;
    this.nonProgramMethods = nonProgramMethods;
  }

  public DexMethodSignatureSet getOrComputeNonProgramMethods(ClasspathOrLibraryClass clazz) {
    return getOrComputeNonProgramMethods(clazz.asDexClass());
  }

  // Parameter is typed as DexClass to account for program classes above classpath or library
  // classes.
  private DexMethodSignatureSet getOrComputeNonProgramMethods(DexClass clazz) {
    DexMethodSignatureSet nonProgramMethodsOnClass = nonProgramMethods.get(clazz);
    return nonProgramMethodsOnClass != null
        ? nonProgramMethodsOnClass
        : computeNonProgramMethods(clazz);
  }

  private DexMethodSignatureSet computeNonProgramMethods(DexClass clazz) {
    DexMethodSignatureSet nonProgramMethodsOnClass = DexMethodSignatureSet.create();
    clazz.forEachImmediateSuperClassMatching(
        appView,
        (supertype, superclass) -> superclass != null,
        (supertype, superclass) ->
            nonProgramMethodsOnClass.addAll(getOrComputeNonProgramMethods(superclass)));
    if (!clazz.isProgramClass()) {
      clazz.forEachClassMethod(
          method -> {
            if (test(method)) {
              nonProgramMethodsOnClass.add(method.getMethodSignature());
            }
          });
    }
    nonProgramMethods.put(clazz, nonProgramMethodsOnClass);
    return nonProgramMethodsOnClass;
  }

  public abstract boolean test(DexClassAndMethod method);
}
