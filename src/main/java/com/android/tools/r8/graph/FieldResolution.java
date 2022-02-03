// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.FieldResolutionResult.SuccessfulFieldResolutionResult;
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
    DexClass holder = definitionFor.contextIndependentDefinitionFor(type);
    return holder != null ? resolveFieldOn(holder, field) : FieldResolutionResult.failure();
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
      return new SuccessfulFieldResolutionResult(initialResolutionHolder, holder, definition);
    }
    // Step 2: Apply recursively to direct superinterfaces. First match succeeds.
    DexClassAndField result = resolveFieldOnDirectInterfaces(holder, field, visitedInterfaces);
    if (result != null) {
      return new SuccessfulFieldResolutionResult(
          initialResolutionHolder, result.getHolder(), result.getDefinition());
    }
    // Step 3: Apply recursively to superclass.
    if (holder.superType != null) {
      DexClass superClass = definitionFor.contextIndependentDefinitionFor(holder.superType);
      if (superClass != null) {
        return resolveFieldOn(superClass, field, initialResolutionHolder, visitedInterfaces);
      }
    }
    return FieldResolutionResult.failure();
  }

  private DexClassAndField resolveFieldOnDirectInterfaces(
      DexClass clazz, DexField field, Set<DexType> visitedInterfaces) {
    for (DexType interfaceType : clazz.interfaces.values) {
      if (visitedInterfaces.add(interfaceType)) {
        DexClass interfaceClass = definitionFor.contextIndependentDefinitionFor(interfaceType);
        if (interfaceClass != null) {
          DexClassAndField result =
              resolveFieldOnInterface(interfaceClass, field, visitedInterfaces);
          if (result != null) {
            return result;
          }
        }
      }
    }
    return null;
  }

  private DexClassAndField resolveFieldOnInterface(
      DexClass interfaceClass, DexField field, Set<DexType> visitedInterfaces) {
    // Step 1: Class declares the field.
    DexEncodedField definition = interfaceClass.lookupField(field);
    if (definition != null) {
      return DexClassAndField.create(interfaceClass, definition);
    }
    // Step 2: Apply recursively to direct superinterfaces. First match succeeds.
    return resolveFieldOnDirectInterfaces(interfaceClass, field, visitedInterfaces);
  }
}
