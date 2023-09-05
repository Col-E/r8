// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.utils.structural.Equatable;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.Objects;

public class DexFieldSignature implements StructuralItem<DexFieldSignature> {

  private final DexString name;
  private final DexType type;

  private static void specify(StructuralSpecification<DexFieldSignature, ?> spec) {
    spec.withItem(DexFieldSignature::getName).withItem(DexFieldSignature::getType);
  }

  public static DexFieldSignature fromField(DexField field) {
    return new DexFieldSignature(field.getName(), field.getType());
  }

  private DexFieldSignature(DexString name, DexType type) {
    this.name = name;
    this.type = type;
  }

  public DexString getName() {
    return name;
  }

  public DexType getType() {
    return type;
  }

  public boolean match(DexField field) {
    return getName().equals(field.getName()) && getType().equals(field.getType());
  }

  @Override
  public DexFieldSignature self() {
    return this;
  }

  @Override
  public StructuralMapping<DexFieldSignature> getStructuralMapping() {
    return DexFieldSignature::specify;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean isEqualTo(DexFieldSignature other) {
    return getName() == other.getName() && getType() == other.getType();
  }

  @Override
  public boolean equals(Object o) {
    return Equatable.equalsImpl(this, o);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type);
  }
}
