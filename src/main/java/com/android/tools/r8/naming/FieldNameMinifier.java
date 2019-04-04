// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.io.PrintStream;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

class FieldNameMinifier extends MemberNameMinifier<DexField, DexType> {

  FieldNameMinifier(AppView<AppInfoWithLiveness> appView, MemberNamingStrategy strategy) {
    super(appView, strategy);
  }

  @Override
  Function<DexType, ?> getKeyTransform() {
    if (overloadAggressively) {
      // Use the type as the key, hence reuse names per type.
      return Function.identity();
    } else {
      // Always use the same key, hence do not reuse names per type.
      return a -> Void.class;
    }
  }

  FieldRenaming computeRenaming(Timing timing) {
    // Reserve names in all classes first. We do this in subtyping order so we do not
    // shadow a reserved field in subclasses. While there is no concept of virtual field
    // dispatch in Java, field resolution still traverses the super type chain and external
    // code might use a subtype to reference the field.
    timing.begin("reserve-classes");
    reserveNamesInSubtypes(appView.dexItemFactory().objectType, globalState);
    timing.end();
    // Next, reserve field names in interfaces. These should only be static.
    timing.begin("reserve-interfaces");
    appView
        .appInfo()
        .forAllInterfaces(
            appView.dexItemFactory(), iface -> reserveNamesInSubtypes(iface, globalState));
    timing.end();
    // Rename the definitions.
    timing.begin("rename-definitions");
    renameFieldsInClasses();
    renameFieldsInInterfaces();
    timing.end();
    // Rename the references that are not rebound to definitions for some reasons.
    timing.begin("rename-references");
    renameNonReboundReferences();
    timing.end();
    return new FieldRenaming(renaming);
  }

  static class FieldRenaming {

    final Map<DexField, DexString> renaming;

    private FieldRenaming(Map<DexField, DexString> renaming) {
      this.renaming = renaming;
    }

    public static FieldRenaming empty() {
      return new FieldRenaming(ImmutableMap.of());
    }
  }

  private void reserveNamesInSubtypes(DexType type, NamingState<DexType, ?> state) {
    DexClass holder = appView.definitionFor(type);
    if (holder == null) {
      return;
    }
    // If there is a mapping file, it can be that fields in libraries should be renamed and
    // therefore not reserved.
    NamingState<DexType, ?> newState = computeStateIfAbsent(type, t -> state.createChild());
    holder.forEachField(
        field -> reserveFieldName(field, newState, alwaysReserveMemberNames(holder)));
    appView
        .appInfo()
        .forAllExtendsSubtypes(type, subtype -> reserveNamesInSubtypes(subtype, newState));
  }

  private void reserveFieldName(
      DexEncodedField encodedField, NamingState<DexType, ?> state, boolean alwaysReserve) {
    DexField field = encodedField.field;
    if (alwaysReserve || appView.rootSet().noObfuscation.contains(field)) {
      state.reserveName(field.name, field.type);
    }
  }

  private void renameFieldsInClasses() {
    renameFieldsInSubclasses(appView.dexItemFactory().objectType, null);
  }

  private void renameFieldsInSubclasses(DexType type, DexType parent) {
    DexClass clazz = appView.definitionFor(type);
    if (clazz == null) {
      return;
    }
    assert !clazz.isInterface();
    assert clazz.superType == parent;

    NamingState<DexType, ?> state = minifierState.getState(clazz.type);
    assert state != null;
    for (DexEncodedField field : clazz.fields()) {
      renameField(field, state);
    }
    for (DexType subclass : appView.appInfo().allExtendsSubtypes(type)) {
      renameFieldsInSubclasses(subclass, type);
    }
  }

  private void renameFieldsInInterfaces() {
    for (DexType interfaceType : appView.appInfo().allInterfaces(appView.dexItemFactory())) {
      renameFieldsInInterface(interfaceType);
    }
  }

  private void renameFieldsInInterface(DexType type) {
    DexClass clazz = appView.definitionFor(type);
    if (clazz == null) {
      return;
    }
    assert clazz.isInterface();
    NamingState<DexType, ?> state = minifierState.getState(clazz.type);
    assert state != null;
    for (DexEncodedField field : clazz.fields()) {
      renameField(field, state);
    }
  }

  private void renameField(DexEncodedField encodedField, NamingState<DexType, ?> state) {
    DexField field = encodedField.field;

    Set<String> loggingFilter = appView.options().extensiveFieldMinifierLoggingFilter;
    if (!loggingFilter.isEmpty()) {
      if (loggingFilter.contains(field.toSourceString())) {
        print(field, state, System.out);
      }
    }

    if (!state.isReserved(field.name, field.type)) {
      renaming.put(
          field, state.assignNewNameFor(field, field.name, field.type, useUniqueMemberNames));
    }
  }

  private void renameNonReboundReferences() {
    // TODO(b/123068484): Collect non-rebound references instead of visiting all references.
    AppInfoWithLiveness appInfo = appView.appInfo();
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
    DexEncodedField definition = appView.definitionFor(field);
    if (definition != null) {
      assert definition.field == field;
      return;
    }
    // Now, `field` is reference. Find its definition and check if it's renamed.
    DexClass holder = appView.definitionFor(field.holder);
    // We don't care pruned types or library classes.
    if (holder == null || holder.isNotProgramClass()) {
      return;
    }
    definition = appView.appInfo().resolveField(field);
    if (definition == null) {
      // The program is already broken in the sense that it has an unresolvable field reference.
      // Leave it as-is.
      return;
    }
    assert definition.field != field;
    assert definition.field.holder != field.holder;
    // If the definition is renamed,
    if (renaming.containsKey(definition.field)) {
      // Assign the same, renamed name as the definition to the reference.
      renaming.put(field, renaming.get(definition.field));
    }
  }

  private void print(DexField field, NamingState<DexType, ?> state, PrintStream out) {
    out.println("--------------------------------------------------------------------------------");
    out.println("FieldNameMinifier(`" + field.toSourceString() + "`)");
    out.println("--------------------------------------------------------------------------------");
    state.printState(field.type, minifierState::getStateKey, "", out);
    out.println();
  }
}
