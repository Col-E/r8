// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.lens;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.utils.IterableUtils;

final class IdentityGraphLens extends GraphLens {

  private static IdentityGraphLens INSTANCE = new IdentityGraphLens();

  private IdentityGraphLens() {}

  static IdentityGraphLens getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isIdentityLens() {
    return true;
  }

  @Override
  public boolean isIdentityLensForFields(GraphLens codeLens) {
    return true;
  }

  @Override
  public boolean isNonIdentityLens() {
    return false;
  }

  @Override
  public DexType getOriginalType(DexType type) {
    return type;
  }

  @Override
  public Iterable<DexType> getOriginalTypes(DexType type) {
    return IterableUtils.singleton(type);
  }

  @Override
  public DexField getOriginalFieldSignature(DexField field) {
    return field;
  }

  @Override
  public DexField getRenamedFieldSignature(DexField originalField, GraphLens codeLens) {
    return originalField;
  }

  @Override
  public DexMethod getRenamedMethodSignature(DexMethod originalMethod, GraphLens applied) {
    return originalMethod;
  }

  @Override
  public String lookupPackageName(String pkg) {
    return pkg;
  }

  @Override
  public DexType lookupType(DexType type, GraphLens applied) {
    return type;
  }

  @Override
  public DexType lookupClassType(DexType type, GraphLens applied) {
    assert type.isClassType();
    return type;
  }

  @Override
  public MethodLookupResult lookupMethod(
      DexMethod method, DexMethod context, InvokeType type, GraphLens codeLens) {
    assert codeLens == null || codeLens.isIdentityLens();
    return MethodLookupResult.builder(this).setReference(method).setType(type).build();
  }

  @Override
  public RewrittenPrototypeDescription lookupPrototypeChangesForMethodDefinition(
      DexMethod method, GraphLens codeLens) {
    return RewrittenPrototypeDescription.none();
  }

  @Override
  protected FieldLookupResult internalLookupField(
      DexField reference, GraphLens codeLens, LookupFieldContinuation continuation) {
    // Passes the field reference back to the next graph lens. The identity lens intentionally
    // does not set the rebound field reference, since it does not know what that is.
    return continuation.lookupField(
        FieldLookupResult.builder(this).setReference(reference).build());
  }

  @Override
  protected MethodLookupResult internalLookupMethod(
      DexMethod reference,
      DexMethod context,
      InvokeType type,
      GraphLens codeLens,
      LookupMethodContinuation continuation) {
    // Passes the method reference back to the next graph lens. The identity lens intentionally
    // does not set the rebound method reference, since it does not know what that is.
    return continuation.lookupMethod(lookupMethod(reference, context, type, codeLens));
  }

  @Override
  public boolean isContextFreeForMethods(GraphLens codeLens) {
    return true;
  }

  @Override
  public boolean hasCodeRewritings() {
    return false;
  }
}
