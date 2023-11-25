// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.Objects;

public abstract class DexMethodSignature implements StructuralItem<DexMethodSignature> {

  DexMethodSignature() {}

  public static DexMethodSignature create(DexMethod method) {
    return new MethodBased(method);
  }

  public static DexMethodSignature create(DexString name, DexProto proto) {
    return new NameAndProtoBased(name, proto);
  }

  public abstract DexString getName();

  public abstract DexProto getProto();

  public int getArity() {
    return getProto().getArity();
  }

  public DexType getParameter(int index) {
    return getProto().getParameter(index);
  }

  public DexTypeList getParameters() {
    return getProto().getParameters();
  }

  public DexType getReturnType() {
    return getProto().getReturnType();
  }

  @Override
  public StructuralMapping<DexMethodSignature> getStructuralMapping() {
    return DexMethodSignature::specify;
  }

  private static void specify(StructuralSpecification<DexMethodSignature, ?> spec) {
    spec.withItem(DexMethodSignature::getName).withItem(DexMethodSignature::getProto);
  }

  public DexMethodSignature withName(DexString name) {
    return create(name, getProto());
  }

  public DexMethodSignature withParameters(DexTypeList parameters, DexItemFactory dexItemFactory) {
    return create(getName(), dexItemFactory.createProto(getReturnType(), parameters));
  }

  public DexMethodSignature withProto(DexProto proto) {
    return create(getName(), proto);
  }

  public DexMethod withHolder(ProgramDefinition definition, DexItemFactory dexItemFactory) {
    return withHolder(definition.getContextType(), dexItemFactory);
  }

  public DexMethod withHolder(DexReference reference, DexItemFactory dexItemFactory) {
    return dexItemFactory.createMethod(reference.getContextType(), getProto(), getName());
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DexMethodSignature)) return false;
    DexMethodSignature that = (DexMethodSignature) o;
    return getName() == that.getName() && getProto() == that.getProto();
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getProto());
  }

  @Override
  public DexMethodSignature self() {
    return this;
  }

  @Override
  public String toString() {
    StringBuilder builder =
        new StringBuilder()
            .append(getReturnType().getTypeName())
            .append(" ")
            .append(getName())
            .append("(");
    for (int i = 0; i < getArity(); i++) {
      if (i != 0) {
        builder.append(", ");
      }
      builder.append(getParameter(i).getTypeName());
    }
    return builder.append(")").toString();
  }

  static class MethodBased extends DexMethodSignature {

    private final DexMethod method;

    MethodBased(DexMethod method) {
      this.method = method;
    }

    @Override
    public DexString getName() {
      return method.getName();
    }

    @Override
    public DexProto getProto() {
      return method.getProto();
    }
  }

  static class NameAndProtoBased extends DexMethodSignature {

    private final DexString name;
    private final DexProto proto;

    NameAndProtoBased(DexString name, DexProto proto) {
      this.name = name;
      this.proto = proto;
    }

    @Override
    public DexString getName() {
      return name;
    }

    @Override
    public DexProto getProto() {
      return proto;
    }
  }
}
