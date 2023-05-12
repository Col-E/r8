// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.ir.optimize.info.FieldOptimizationInfo;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public abstract class DexClassAndField extends DexClassAndMember<DexEncodedField, DexField> {

  DexClassAndField(DexClass holder, DexEncodedField field) {
    super(holder, field);
    assert holder.isClasspathClass() == (this instanceof ClasspathField);
    assert holder.isLibraryClass() == (this instanceof LibraryField);
    assert holder.isProgramClass() == (this instanceof ProgramField);
  }

  public static DexClassAndField create(DexClass holder, DexEncodedField field) {
    if (holder.isProgramClass()) {
      return new ProgramField(holder.asProgramClass(), field);
    }
    if (holder.isLibraryClass()) {
      return new LibraryField(holder.asLibraryClass(), field);
    }
    assert holder.isClasspathClass();
    return new ClasspathField(holder.asClasspathClass(), field);
  }

  @Override
  public FieldAccessFlags getAccessFlags() {
    return getDefinition().getAccessFlags();
  }

  public FieldReference getFieldReference() {
    return getReference().asFieldReference();
  }

  @Override
  public FieldOptimizationInfo getOptimizationInfo() {
    return getDefinition().getOptimizationInfo();
  }

  public DexType getType() {
    return getReference().getType();
  }

  @Override
  public boolean isField() {
    return true;
  }

  @Override
  public DexClassAndField asField() {
    return this;
  }

  @Override
  public DexClassAndField asMember() {
    return this;
  }

  public final boolean isFinalOrEffectivelyFinal(AppView<?> appView) {
    return getAccessFlags().isFinal()
        || (appView.hasLiveness() && isEffectivelyFinal(appView.withLiveness()));
  }

  public boolean isEffectivelyFinal(AppView<AppInfoWithLiveness> appView) {
    return false;
  }
}
