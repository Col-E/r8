// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class DexMethod extends DexMember<DexEncodedMethod, DexMethod> {

  public final DexProto proto;

  DexMethod(DexType holder, DexProto proto, DexString name, boolean skipNameValidationForTesting) {
    super(holder, name);
    this.proto = proto;
    if (!skipNameValidationForTesting && !name.isValidMethodName()) {
      throw new CompilationError(
          "Method name '" + name + "' in class '" + holder.toSourceString() +
              "' cannot be represented in dex format.");
    }
  }

  public DexType getHolderType() {
    return holder;
  }

  public DexString getName() {
    return name;
  }

  public DexType getParameter(int index) {
    return proto.getParameter(index);
  }

  public DexTypeList getParameters() {
    return proto.parameters;
  }

  public DexType getReturnType() {
    return proto.returnType;
  }

  @Override
  public <T> T apply(
      Function<DexType, T> classConsumer,
      Function<DexField, T> fieldConsumer,
      Function<DexMethod, T> methodConsumer) {
    return methodConsumer.apply(this);
  }

  @Override
  public void accept(
      Consumer<DexType> classConsumer,
      Consumer<DexField> fieldConsumer,
      Consumer<DexMethod> methodConsumer) {
    methodConsumer.accept(this);
  }

  @Override
  public <T> void accept(
      BiConsumer<DexType, T> classConsumer,
      BiConsumer<DexField, T> fieldConsumer,
      BiConsumer<DexMethod, T> methodConsumer,
      T arg) {
    methodConsumer.accept(this, arg);
  }

  @Override
  public DexEncodedMethod lookupOnClass(DexClass clazz) {
    return clazz != null ? clazz.lookupMember(this) : null;
  }

  @Override
  public ProgramMethod lookupOnProgramClass(DexProgramClass clazz) {
    return clazz != null ? clazz.lookupProgramMethod(this) : null;
  }

  @Override
  public String toString() {
    return "Method " + holder + "." + name + " " + proto.toString();
  }

  public MethodReference asMethodReference() {
    List<TypeReference> parameters = new ArrayList<>();
    for (DexType value : proto.parameters.values) {
      parameters.add(Reference.typeFromDescriptor(value.toDescriptorString()));
    }
    String returnTypeDescriptor = proto.returnType.toDescriptorString();
    TypeReference returnType =
        returnTypeDescriptor.equals("V")
            ? null
            : Reference.typeFromDescriptor(returnTypeDescriptor);
    return Reference.method(
        Reference.classFromDescriptor(holder.toDescriptorString()),
        name.toString(),
        parameters,
        returnType);
  }

  public int getArity() {
    return proto.parameters.size();
  }

  public DexMethodSignature getSignature() {
    return new DexMethodSignature(this);
  }

  @Override
  public void collectIndexedItems(IndexedItemCollection indexedItems) {
    if (collectIndexedItemsExceptName(indexedItems)) {
      collectIndexedItemsName(indexedItems);
    }
  }

  boolean collectIndexedItemsExceptName(IndexedItemCollection indexedItems) {
    if (indexedItems.addMethod(this)) {
      holder.collectIndexedItems(indexedItems);
      proto.collectIndexedItems(indexedItems);
      return true;
    }
    return false;
  }

  void collectIndexedItemsName(IndexedItemCollection indexedItems) {
    indexedItems.getRenamedName(this).collectIndexedItems(indexedItems);
  }

  @Override
  public int getOffset(ObjectToOffsetMapping mapping) {
    return mapping.getOffsetFor(this);
  }

  @Override
  public boolean isDexMethod() {
    return true;
  }

  @Override
  public DexMethod asDexMethod() {
    return this;
  }

  @Override
  public Iterable<DexType> getReferencedTypes() {
    return proto.getTypes();
  }

  @Override
  public int computeHashCode() {
    return holder.hashCode()
        + proto.hashCode() * 7
        + name.hashCode() * 31;
  }

  @Override
  public boolean computeEquals(Object other) {
    if (other instanceof DexMethod) {
      DexMethod o = (DexMethod) other;
      return holder.equals(o.holder)
          && name.equals(o.name)
          && proto.equals(o.proto);
    }
    return false;
  }

  /**
   * Returns true if the other method has the same name and prototype (including signature and
   * return type), false otherwise.
   */
  public boolean hasSameProtoAndName(DexMethod other) {
    return name == other.name && proto == other.proto;
  }

  @Override
  public int slowCompareTo(DexMethod other) {
    int result = holder.slowCompareTo(other.holder);
    if (result != 0) {
      return result;
    }
    result = name.slowCompareTo(other.name);
    if (result != 0) {
      return result;
    }
    return proto.slowCompareTo(other.proto);
  }

  @Override
  public int slowCompareTo(DexMethod other, NamingLens namingLens) {
    int result = holder.slowCompareTo(other.holder, namingLens);
    if (result != 0) {
      return result;
    }
    result = namingLens.lookupName(this).slowCompareTo(namingLens.lookupName(other));
    if (result != 0) {
      return result;
    }
    return proto.slowCompareTo(other.proto, namingLens);
  }

  @Override
  public boolean match(DexMethod method) {
    return method.name == name && method.proto == proto;
  }

  @Override
  public boolean match(DexEncodedMethod encodedMethod) {
    return match(encodedMethod.method);
  }

  public String qualifiedName() {
    return holder + "." + name;
  }

  @Override
  public String toSmaliString() {
    return holder.toSmaliString() + "->" + name + proto.toSmaliString();
  }

  @Override
  public String toSourceString() {
    return toSourceString(true, true);
  }

  public String toSourceStringWithoutHolder() {
    return toSourceString(false, true);
  }

  public String toSourceStringWithoutHolderAndReturnType() {
    return toSourceString(false, false);
  }

  private String toSourceString(boolean includeHolder, boolean includeReturnType) {
    StringBuilder builder = new StringBuilder();
    if (includeReturnType) {
      builder.append(getReturnType().toSourceString()).append(" ");
    }
    if (includeHolder) {
      builder.append(holder.toSourceString()).append(".");
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

  public boolean isLambdaDeserializeMethod(DexItemFactory dexItemFactory) {
    return name == dexItemFactory.deserializeLambdaMethodName
        && proto == dexItemFactory.deserializeLambdaMethodProto;
  }

  public boolean isInstanceInitializer(DexItemFactory factory) {
    return factory.isConstructor(this);
  }

  public DexMethod withExtraArgumentPrepended(DexType type, DexItemFactory dexItemFactory) {
    return dexItemFactory.createMethod(
        holder, dexItemFactory.prependTypeToProto(type, proto), name);
  }

  public DexMethod withHolder(DexType holder, DexItemFactory dexItemFactory) {
    return dexItemFactory.createMethod(holder, proto, name);
  }

  public DexMethod withName(DexString name, DexItemFactory dexItemFactory) {
    return dexItemFactory.createMethod(holder, proto, name);
  }
}
