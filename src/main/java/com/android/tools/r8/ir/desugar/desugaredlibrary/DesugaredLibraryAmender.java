// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassResolutionResult;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.FieldAccessFlags;
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
        appView.options().machineDesugaredLibrarySpecification.getAmendLibraryFields(),
        appView,
        appView.options().reporter,
        appView.computedMinApiLevel());
  }

  public static void run(
      Map<DexMethod, MethodAccessFlags> amendLibraryMethod,
      Map<DexField, FieldAccessFlags> amendLibraryField,
      DexDefinitionSupplier definitions,
      Reporter reporter,
      ComputedApiLevel minAPILevel) {
    if (amendLibraryMethod.isEmpty() && amendLibraryField.isEmpty()) {
      return;
    }
    new DesugaredLibraryAmender(definitions, reporter, minAPILevel)
        .run(amendLibraryMethod, amendLibraryField);
  }

  private DesugaredLibraryAmender(
      DexDefinitionSupplier definitions, Reporter reporter, ComputedApiLevel minAPILevel) {
    this.definitions = definitions;
    this.reporter = reporter;
    this.minAPILevel = minAPILevel;
  }

  private void run(
      Map<DexMethod, MethodAccessFlags> amendLibraryMethod,
      Map<DexField, FieldAccessFlags> amendLibraryField) {
    amendLibraryMethod.forEach(this::amendLibraryMethod);
    amendLibraryField.forEach(this::amendLibraryField);
  }

  private void amendLibraryField(DexField field, FieldAccessFlags fieldAccessFlags) {
    DexLibraryClass libClass = getLibraryClass(field);
    if (libClass == null) {
      return;
    }
    if (libClass.lookupField(field) != null) {
      return;
    }
    DexEncodedField encodedField =
        DexEncodedField.syntheticBuilder()
            .setField(field)
            .setAccessFlags(fieldAccessFlags)
            .setApiLevel(minAPILevel)
            .build();
    if (fieldAccessFlags.isStatic()) {
      libClass.appendStaticField(encodedField);
    } else {
      libClass.appendInstanceField(encodedField);
    }
  }

  private void amendLibraryMethod(DexMethod method, MethodAccessFlags methodAccessFlags) {
    DexLibraryClass libClass = getLibraryClass(method);
    if (libClass == null) {
      return;
    }
    if (libClass.lookupMethod(method) != null) {
      return;
    }
    DexEncodedMethod encodedMethod =
        DexEncodedMethod.syntheticBuilder()
            .setMethod(method)
            .setAccessFlags(methodAccessFlags)
            .setApiLevelForDefinition(minAPILevel)
            .build();
    libClass.getMethodCollection().addMethod(encodedMethod);
  }

  private DexLibraryClass getLibraryClass(DexReference reference) {
    DexClass dexClass = definitions.contextIndependentDefinitionFor(reference.getContextType());
    if (dexClass == null || !dexClass.isLibraryClass()) {
      // We can end up in situation where a class is rewritten at some API level but is used as
      // a library class with a different min API level. We check here if there is a multiple
      // resolution result.
      ClassResolutionResult result =
          definitions.contextIndependentDefinitionForWithResolutionResult(
              reference.getContextType());
      if (result.isMultipleClassResolutionResult()) {
        DexClass alternativeClass = result.toAlternativeClass();
        if (alternativeClass != null && alternativeClass.isLibraryClass()) {
          return alternativeClass.asLibraryClass();
        }
      }
      reporter.warning(
          "Desugared library: Cannot amend library reference "
              + reference
              + " because the holder is not a library class"
              + (dexClass == null ? "(null)." : "."));
      return null;
    }
    return dexClass.asLibraryClass();
  }
}
