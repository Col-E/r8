// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import com.google.common.collect.Iterables;
import java.util.Collections;
import java.util.function.Consumer;

public class DexProto extends IndexedDexItem
    implements NamingLensComparable<DexProto>, LirConstant {

  public static final DexProto SENTINEL = new DexProto(null, null, null);

  public final DexString shorty;
  public final DexType returnType;
  public final DexTypeList parameters;

  DexProto(DexString shorty, DexType returnType, DexTypeList parameters) {
    this.shorty = shorty;
    this.returnType = returnType;
    this.parameters = parameters;
  }

  private static void specify(StructuralSpecification<DexProto, ?> spec) {
    spec.withItem(DexProto::getReturnType)
        .withItem(p -> p.parameters)
        // TODO(b/172206529): Consider removing shorty.
        .withItem(p1 -> p1.shorty);
  }

  @Override
  public StructuralMapping<DexProto> getStructuralMapping() {
    return DexProto::specify;
  }

  @Override
  public DexProto self() {
    return this;
  }

  @Override
  public boolean computeEquals(Object other) {
    if (other instanceof DexProto) {
      DexProto o = (DexProto) other;
      return shorty.equals(o.shorty)
          && returnType.equals(o.returnType)
          && parameters.equals(o.parameters);
    }
    return false;
  }

  @Override
  public int computeHashCode() {
    return returnType.hashCode() * 7 + parameters.hashCode() * 13;
  }

  public DexType getReturnType() {
    return returnType;
  }

  public Iterable<DexType> getParameterBaseTypes(DexItemFactory dexItemFactory) {
    return Iterables.transform(parameters, type -> type.toBaseType(dexItemFactory));
  }

  public Iterable<DexType> getBaseTypes(DexItemFactory dexItemFactory) {
    return Iterables.transform(getTypes(), type -> type.toBaseType(dexItemFactory));
  }

  public Iterable<DexType> getTypes() {
    return Iterables.concat(Collections.singleton(returnType), parameters);
  }

  public void forEachType(Consumer<DexType> consumer) {
    consumer.accept(returnType);
    parameters.forEach(consumer);
  }

  public DexType getParameter(int index) {
    return parameters.values[index];
  }

  public DexTypeList getParameters() {
    return parameters;
  }

  public int getArity() {
    return parameters.size();
  }

  public DexProto prependParameter(DexType parameter, DexItemFactory dexItemFactory) {
    DexType[] parameterTypes = new DexType[getParameters().size() + 1];
    parameterTypes[0] = parameter;
    System.arraycopy(getParameters().getBacking(), 0, parameterTypes, 1, getParameters().size());
    return dexItemFactory.createProto(getReturnType(), parameterTypes);
  }

  @Override
  public String toString() {
    return "Proto " + shorty + " " + returnType + " " + parameters;
  }

  public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
    if (indexedItems.addProto(this)) {
      shorty.collectIndexedItems(indexedItems);
      returnType.collectIndexedItems(appView, indexedItems);
      parameters.collectIndexedItems(appView, indexedItems);
    }
  }

  @Override
  public int getOffset(ObjectToOffsetMapping mapping) {
    return mapping.getOffsetFor(this);
  }

  @Override
  public String toSmaliString() {
    return toDescriptorString();
  }

  public String toDescriptorString() {
    return toDescriptorString(NamingLens.getIdentityLens());
  }

  public String toDescriptorString(NamingLens lens) {
    StringBuilder builder = new StringBuilder();
    builder.append("(");
    for (int i = 0; i < parameters.values.length; i++) {
      builder.append(lens.lookupDescriptor(parameters.values[i]));
    }
    builder.append(")");
    builder.append(lens.lookupDescriptor(returnType));
    return builder.toString();
  }
}
