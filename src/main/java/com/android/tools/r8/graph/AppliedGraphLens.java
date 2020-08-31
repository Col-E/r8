// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.utils.MapUtils;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A graph lens that will not lead to any code rewritings in the {@link
 * com.android.tools.r8.ir.conversion.LensCodeRewriter}, or parameter removals in the {@link
 * com.android.tools.r8.ir.conversion.IRBuilder}.
 *
 * <p>The mappings from the original program to the generated program are kept, though.
 */
public class AppliedGraphLens extends GraphLens {

  private final AppView<?> appView;

  private final BiMap<DexType, DexType> originalTypeNames = HashBiMap.create();
  private final BiMap<DexField, DexField> originalFieldSignatures = HashBiMap.create();
  private final BiMap<DexMethod, DexMethod> originalMethodSignatures = HashBiMap.create();

  // Original method signatures for bridges and companion methods. Due to the synthesis of these
  // methods, the mapping from original methods to final methods is not one-to-one, but one-to-many,
  // which is why we need an additional map.
  private final Map<DexMethod, DexMethod> extraOriginalMethodSignatures = new IdentityHashMap<>();

  public AppliedGraphLens(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;

    for (DexProgramClass clazz : appView.appInfo().classes()) {
      // Record original type names.
      {
        DexType type = clazz.type;
        if (appView.verticallyMergedClasses() != null
            && !appView.verticallyMergedClasses().hasBeenMergedIntoSubtype(type)) {
          DexType original = appView.graphLens().getOriginalType(type);
          if (original != type) {
            DexType existing = originalTypeNames.forcePut(type, original);
            assert existing == null;
          }
        }
      }

      // Record original field signatures.
      for (DexEncodedField encodedField : clazz.fields()) {
        DexField field = encodedField.field;
        DexField original = appView.graphLens().getOriginalFieldSignature(field);
        if (original != field) {
          DexField existing = originalFieldSignatures.forcePut(field, original);
          assert existing == null;
        }
      }

      // Record original method signatures.
      for (DexEncodedMethod encodedMethod : clazz.methods()) {
        DexMethod method = encodedMethod.method;
        DexMethod original = appView.graphLens().getOriginalMethodSignature(method);
        DexMethod existing = originalMethodSignatures.inverse().get(original);
        if (existing == null) {
          originalMethodSignatures.put(method, original);
        } else {
          DexMethod renamed = appView.graphLens().getRenamedMethodSignature(original);
          if (renamed == existing) {
            extraOriginalMethodSignatures.put(method, original);
          } else {
            originalMethodSignatures.forcePut(method, original);
            extraOriginalMethodSignatures.put(existing, original);
          }
        }
      }
    }

    // Trim original method signatures.
    MapUtils.removeIdentityMappings(originalMethodSignatures);
    MapUtils.removeIdentityMappings(extraOriginalMethodSignatures);
  }

  @Override
  public DexType getOriginalType(DexType type) {
    return originalTypeNames.getOrDefault(type, type);
  }

  @Override
  public DexField getOriginalFieldSignature(DexField field) {
    return originalFieldSignatures.getOrDefault(field, field);
  }

  @Override
  public DexMethod getOriginalMethodSignature(DexMethod method) {
    if (extraOriginalMethodSignatures.containsKey(method)) {
      return extraOriginalMethodSignatures.get(method);
    }
    return originalMethodSignatures.getOrDefault(method, method);
  }

  @Override
  public DexField getRenamedFieldSignature(DexField originalField) {
    return originalFieldSignatures.inverse().getOrDefault(originalField, originalField);
  }

  @Override
  public DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied) {
    return this != applied
        ? originalMethodSignatures.inverse().getOrDefault(originalMethod, originalMethod)
        : originalMethod;
  }

  @Override
  public DexType lookupType(DexType type) {
    if (appView.verticallyMergedClasses() != null
        && appView.verticallyMergedClasses().hasBeenMergedIntoSubtype(type)) {
      return lookupType(appView.verticallyMergedClasses().getTargetFor(type));
    }
    return originalTypeNames.inverse().getOrDefault(type, type);
  }

  @Override
  public GraphLensLookupResult lookupMethod(DexMethod method, DexMethod context, Invoke.Type type) {
    return GraphLens.getIdentityLens().lookupMethod(method, context, type);
  }

  @Override
  public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(DexMethod method) {
    return GraphLens.getIdentityLens().lookupPrototypeChangesForMethodDefinition(method);
  }

  @Override
  public DexField lookupField(DexField field) {
    return field;
  }

  @Override
  public boolean isContextFreeForMethods() {
    return true;
  }

  @Override
  public boolean hasCodeRewritings() {
    return false;
  }
}
