// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.function.Function;

class FieldNameMinifier extends MemberNameMinifier<DexField, DexType> {

  FieldNameMinifier(AppInfoWithLiveness appInfo, RootSet rootSet, InternalOptions options) {
    super(appInfo, rootSet, options);
  }

  @Override
  Function<DexType, ?> getKeyTransform() {
    if (overloadAggressively) {
      // Use the type as the key, hence reuse names per type.
      return a -> a;
    } else {
      // Always use the same key, hence do not reuse names per type.
      return a -> Void.class;
    }
  }

  Map<DexField, DexString> computeRenaming(Timing timing) {
    // Reserve names in all classes first. We do this in subtyping order so we do not
    // shadow a reserved field in subclasses. While there is no concept of virtual field
    // dispatch in Java, field resolution still traverses the super type chain and external
    // code might use a subtype to reference the field.
    timing.begin("reserve-classes");
    reserveNamesInSubtypes(appInfo.dexItemFactory.objectType, globalState);
    timing.end();
    // Next, reserve field names in interfaces. These should only be static.
    timing.begin("reserve-interfaces");
    DexType.forAllInterfaces(appInfo.dexItemFactory,
        iface -> reserveNamesInSubtypes(iface, globalState));
    timing.end();
    // Rename the definitions.
    timing.begin("rename-definitions");
    renameFieldsInSubtypes(appInfo.dexItemFactory.objectType);
    DexType.forAllInterfaces(appInfo.dexItemFactory, this::renameFieldsInSubtypes);
    timing.end();
    // Rename the references that are not rebound to definitions for some reasons.
    timing.begin("rename-references");
    renameNonReboundReferences();
    timing.end();
    return renaming;
  }

  private void reserveNamesInSubtypes(DexType type, NamingState<DexType, ?> state) {
    DexClass holder = appInfo.definitionFor(type);
    if (holder == null) {
      return;
    }
    NamingState<DexType, ?> newState = computeStateIfAbsent(type, t -> state.createChild());
    holder.forEachField(field -> reserveFieldName(field, newState, holder.isLibraryClass()));
    type.forAllExtendsSubtypes(subtype -> reserveNamesInSubtypes(subtype, newState));
  }

  private void reserveFieldName(
      DexEncodedField encodedField,
      NamingState<DexType, ?> state,
      boolean isLibrary) {
    DexField field = encodedField.field;
    if (isLibrary || rootSet.noObfuscation.contains(field)) {
      state.reserveName(field.name, field.type);
    }
  }

  private void renameFieldsInSubtypes(DexType type) {
    DexClass clazz = appInfo.definitionFor(type);
    if (clazz == null) {
      return;
    }
    NamingState<DexType, ?> state = getState(clazz.type);
    assert state != null;
    clazz.forEachField(field -> renameField(field, state));
    type.forAllExtendsSubtypes(this::renameFieldsInSubtypes);
  }

  private void renameField(DexEncodedField encodedField, NamingState<DexType, ?> state) {
    DexField field = encodedField.field;
    if (!state.isReserved(field.name, field.type)) {
      renaming.put(
          field,
          state.assignNewNameFor(field.name, field.type, useUniqueMemberNames));
    }
  }

  private void renameNonReboundReferences() {
    // TODO(b/123068484): Collect non-rebound references instead of visiting all references.
    Sets.union(
        Sets.union(appInfo.staticFieldReads.keySet(), appInfo.staticFieldWrites.keySet()),
        Sets.union(appInfo.instanceFieldReads.keySet(), appInfo.instanceFieldWrites.keySet()))
        .forEach(this::renameNonReboundReference);
  }

  private void renameNonReboundReference(DexField field) {
    // Already renamed
    if (renaming.containsKey(field)) {
      return;
    }
    DexEncodedField definition = appInfo.definitionFor(field);
    if (definition != null) {
      assert definition.field == field;
      return;
    }
    // Now, `field` is reference. Find its definition and check if it's renamed.
    DexType holderType = field.getHolder();
    DexClass holder = appInfo.definitionFor(holderType);
    // We don't care pruned types or library classes.
    if (holder == null || holder.isLibraryClass()) {
      return;
    }
    definition = appInfo.resolveFieldOn(holderType, field);
    if (definition == null) {
      // The program is already broken in the sense that it has an unresolvable field reference.
      // Leave it as-is.
      return;
    }
    assert definition.field != field;
    assert definition.field.getHolder() != holderType;
    // If the definition is renamed,
    if (renaming.containsKey(definition.field)) {
      // Assign the same, renamed name as the definition to the reference.
      renaming.put(field, renaming.get(definition.field));
    }
  }
}
