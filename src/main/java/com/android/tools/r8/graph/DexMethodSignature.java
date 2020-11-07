// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import java.util.Objects;

public class DexMethodSignature {
  private final DexProto proto;
  private final DexString name;

  public DexMethodSignature(DexMethod method) {
    this(method.proto, method.name);
  }

  public DexMethodSignature(DexProto proto, DexString name) {
    assert proto != null;
    assert name != null;
    this.proto = proto;
    this.name = name;
  }

  public DexProto getProto() {
    return proto;
  }

  public DexString getName() {
    return name;
  }

  public DexMethodSignature withName(DexString name) {
    return new DexMethodSignature(proto, name);
  }

  public DexMethodSignature withProto(DexProto proto) {
    return new DexMethodSignature(proto, name);
  }

  public DexMethod withHolder(ProgramDefinition definition, DexItemFactory dexItemFactory) {
    return withHolder(definition.getContextType(), dexItemFactory);
  }

  public DexMethod withHolder(DexReference reference, DexItemFactory dexItemFactory) {
    return dexItemFactory.createMethod(reference.getContextType(), proto, name);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DexMethodSignature that = (DexMethodSignature) o;
    return proto == that.proto && name == that.name;
  }

  @Override
  public int hashCode() {
    return Objects.hash(proto, name);
  }

  public DexType getReturnType() {
    return proto.returnType;
  }

  public int getArity() {
    return proto.getArity();
  }

  @Override
  public String toString() {
    return "Method Signature " + name + " " + proto.toString();
  }

  private String toSourceString() {
    return toSourceString(false);
  }

  private String toSourceString(boolean includeReturnType) {
    StringBuilder builder = new StringBuilder();
    if (includeReturnType) {
      builder.append(getReturnType().toSourceString()).append(" ");
    }
    builder.append(name).append("(");
    for (int i = 0; i < getArity(); i++) {
      if (i != 0) {
        builder.append(", ");
      }
      builder.append(proto.parameters.values[i].toSourceString());
    }
    return builder.append(")").toString();
  }
}
