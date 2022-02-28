// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.utils.Reporter;
import java.util.Map;

/**
 * The LibraryAmender is responsible in amending the library so that desugared library can be
 * applied. For example, it can insert missing methods which are not present in the library but are
 * supported in desugared library.
 */
public class DesugaredLibraryAmender {

  private final DexDefinitionSupplier definitions;
  private final Reporter reporter;
  private final ComputedApiLevel minAPILevel;

  public static void run(AppView<?> appView) {
    run(
        appView.options().machineDesugaredLibrarySpecification.getAmendLibraryMethods(),
        appView,
        appView.options().reporter,
        appView.computedMinApiLevel());
  }

  public static void run(
      Map<DexMethod, MethodAccessFlags> amendLibrary,
      DexDefinitionSupplier definitions,
      Reporter reporter,
      ComputedApiLevel minAPILevel) {
    if (amendLibrary.isEmpty()) {
      return;
    }
    new DesugaredLibraryAmender(definitions, reporter, minAPILevel).run(amendLibrary);
  }

  private DesugaredLibraryAmender(
      DexDefinitionSupplier definitions, Reporter reporter, ComputedApiLevel minAPILevel) {
    this.definitions = definitions;
    this.reporter = reporter;
    this.minAPILevel = minAPILevel;
  }

  private void run(Map<DexMethod, MethodAccessFlags> amendLibrary) {
    amendLibrary.forEach(this::amendLibraryMethod);
  }

  private void amendLibraryMethod(DexMethod method, MethodAccessFlags methodAccessFlags) {
    DexClass dexClass = definitions.contextIndependentDefinitionFor(method.getHolderType());
    if (dexClass == null || !dexClass.isLibraryClass()) {
      // Consider just throwing an error.
      reporter.warning(
          "Desugared library: Cannot amend library method "
              + method
              + " because the holder is not a library class"
              + (dexClass == null ? "(null)." : "."));
      return;
    }
    if (dexClass.lookupMethod(method) != null) {
      return;
    }
    DexEncodedMethod encodedMethod =
        DexEncodedMethod.syntheticBuilder()
            .setMethod(method)
            .setAccessFlags(methodAccessFlags)
            .setCode(null)
            .setApiLevelForDefinition(minAPILevel)
            .build();
    dexClass.getMethodCollection().addMethod(encodedMethod);
  }
}
