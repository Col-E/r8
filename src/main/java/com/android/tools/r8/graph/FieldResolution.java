// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.FieldResolutionResult.createSingleFieldResolutionResult;

import com.android.tools.r8.utils.SetUtils;
import java.util.Set;

/**
 * Implements resolution of a field descriptor against a type.
 *
 * <p>See <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html#jvms-5.4.3.2">
 * Section 5.4.3.2 of the JVM Spec</a>.
 */
public class FieldResolution {

  private final DexDefinitionSupplier definitionFor;

  public FieldResolution(DexDefinitionSupplier definitionFor) {
    this.definitionFor = definitionFor;
  }

  public FieldResolutionResult resolveFieldOn(DexType type, DexField field) {
    FieldResolutionResult.Builder builder = FieldResolutionResult.builder();
    definitionFor
        .contextIndependentDefinitionForWithResolutionResult(type)
        .forEachClassResolutionResult(
            clazz -> builder.addResolutionResult(resolveFieldOn(clazz, field)));
    return builder.buildOrIfEmpty(FieldResolutionResult.failure());
  }

  public FieldResolutionResult resolveFieldOn(DexClass holder, DexField field) {
    assert holder != null;
    return resolveFieldOn(holder, field, holder, SetUtils.newIdentityHashSet(8));
  }

  private FieldResolutionResult resolveFieldOn(
      DexClass holder,
      DexField field,
      DexClass initialResolutionHolder,
      Set<DexType> visitedInterfaces) {
    assert holder != null;
    // Step 1: Class declares the field.
    DexEncodedField definition = holder.lookupField(field);
    if (definition != null) {
      return createSingleFieldResolutionResult(initialResolutionHolder, holder, definition);
    }
    // Step 2: Apply recursively to direct superinterfaces. First match succeeds.
    FieldResolutionResult result =
        resolveFieldOnDirectInterfaces(initialResolutionHolder, holder, field, visitedInterfaces);
    if (result != null) {
      return result;
    }
    // Step 3: Apply recursively to superclass.
    if (holder.superType != null) {
      FieldResolutionResult.Builder builder = FieldResolutionResult.builder();
      definitionFor
          .contextIndependentDefinitionForWithResolutionResult(holder.superType)
          .forEachClassResolutionResult(
              superClass -> {
                // Check if the subtype is a library type and if it is child of a non-library type.
                // If that is the case, do not return any results.
                if (holder.isLibraryClass() && !superClass.isLibraryClass()) {
                  return;
                }
                builder.addResolutionResult(
                    resolveFieldOn(superClass, field, initialResolutionHolder, visitedInterfaces));
              });
      return builder.buildOrIfEmpty(null);
    }
    return FieldResolutionResult.failure();
  }

  private FieldResolutionResult resolveFieldOnDirectInterfaces(
      DexClass initialResolutionHolder,
      DexClass clazz,
      DexField field,
      Set<DexType> visitedInterfaces) {
    for (DexType interfaceType : clazz.interfaces.values) {
      if (visitedInterfaces.add(interfaceType)) {
        FieldResolutionResult.Builder builder = FieldResolutionResult.builder();
        definitionFor
            .contextIndependentDefinitionForWithResolutionResult(interfaceType)
            .forEachClassResolutionResult(
                ifaceClass -> {
                  // Check if the subtype is a library type and if it is child of a non-library
                  // type. If that is the case, do not return any results.
                  if (clazz.isLibraryClass() && !ifaceClass.isLibraryClass()) {
                    return;
                  }
                  builder.addResolutionResult(
                      resolveFieldOnInterface(
                          initialResolutionHolder, ifaceClass, field, visitedInterfaces));
                });
        FieldResolutionResult fieldResolutionResult = builder.buildOrIfEmpty(null);
        if (fieldResolutionResult != null) {
          return fieldResolutionResult;
        }
      }
    }
    return null;
  }

  private FieldResolutionResult resolveFieldOnInterface(
      DexClass initialResolutionHolder,
      DexClass interfaceClass,
      DexField field,
      Set<DexType> visitedInterfaces) {
    // Step 1: Class declares the field.
    DexEncodedField definition = interfaceClass.lookupField(field);
    if (definition != null) {
      return createSingleFieldResolutionResult(initialResolutionHolder, interfaceClass, definition);
    }
    // Step 2: Apply recursively to direct superinterfaces. First match succeeds.
    return resolveFieldOnDirectInterfaces(
        initialResolutionHolder, interfaceClass, field, visitedInterfaces);
  }
}
