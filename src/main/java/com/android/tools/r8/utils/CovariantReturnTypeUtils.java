// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.androidapi.CovariantReturnTypeMethods;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.MethodAccessFlags;

public class CovariantReturnTypeUtils {

  public static void modelLibraryMethodsWithCovariantReturnTypes(AppView<?> appView) {
    CovariantReturnTypeMethods.registerMethodsWithCovariantReturnType(
        appView.dexItemFactory(),
        method -> {
          DexLibraryClass libraryClass =
              DexLibraryClass.asLibraryClassOrNull(
                  appView.appInfo().definitionForWithoutExistenceAssert(method.getHolderType()));
          if (libraryClass == null) {
            return;
          }
          // Check if the covariant method exists on the class.
          DexEncodedMethod covariantMethod = libraryClass.lookupMethod(method);
          if (covariantMethod != null) {
            return;
          }
          libraryClass.addVirtualMethod(
              DexEncodedMethod.builder()
                  .setMethod(method)
                  .setAccessFlags(MethodAccessFlags.builder().setPublic().build())
                  .setApiLevelForDefinition(ComputedApiLevel.notSet())
                  .build());
        });
  }
}
