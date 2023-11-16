// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class DexMethod extends DexMember<DexEncodedMethod, DexMethod> {

  @SuppressWarnings("ReferenceEquality")
  public static boolean identical(DexMethod t1, DexMethod t2) {
    return t1 == t2;
  }

  public final boolean isIdenticalTo(DexMethod other) {
    return identical(this, other);
  }

  public final boolean isNotIdenticalTo(DexMethod other) {
    return !isIdenticalTo(other);
  }

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

  private static void specify(StructuralSpecification<DexMethod, ?> spec) {
    spec.withItem(DexMethod::getHolderType).withItem(DexMethod::getName).withItem(m -> m.proto);
  }

  @Override
  public int compareTo(DexReference other) {
    if (other.isDexMethod()) {
      return compareTo(other.asDexMethod());
    }
    int comparisonResult = getHolderType().compareTo(other.getContextType());
    return comparisonResult != 0 ? comparisonResult : 1;
  }

  @Override
  public StructuralMapping<DexMethod> getStructuralMapping() {
    return DexMethod::specify;
  }

  @Override
  public DexMethod self() {
    return this;
  }

  @Override
  public int acceptCompareTo(DexMethod other, CompareToVisitor visitor) {
    return visitor.visitDexMethod(this, other);
  }

  @Override
  public void acceptHashing(HashingVisitor visitor) {
    visitor.visitDexMethod(this);
  }

  @Override
  public LirConstantOrder getLirConstantOrder() {
    return LirConstantOrder.METHOD;
  }

  @Override
  public int internalLirConstantAcceptCompareTo(LirConstant other, CompareToVisitor visitor) {
    return acceptCompareTo((DexMethod) other, visitor);
  }

  @Override
  public void internalLirConstantAcceptHashing(HashingVisitor visitor) {
    acceptHashing(visitor);
  }

  public DexType getArgumentType(int argumentIndex, boolean isStatic) {
    if (isStatic) {
      return getParameter(argumentIndex);
    }
    if (argumentIndex == 0) {
      return getHolderType();
    }
    return getParameter(argumentIndex - 1);
  }

  public int getNumberOfArguments(boolean isStatic) {
    return getArity() + BooleanUtils.intValue(!isStatic);
  }

  public DexType getParameter(int index) {
    return proto.getParameter(index);
  }

  public DexTypeList getParameters() {
    return proto.parameters;
  }

  public DexProto getProto() {
    return proto;
  }

  public DexType getReturnType() {
    return proto.returnType;
  }

  @Override
  public <T> T apply(Function<DexField, T> fieldConsumer, Function<DexMethod, T> methodConsumer) {
    return methodConsumer.apply(this);
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
  public DexClassAndMethod lookupMemberOnClass(DexClass clazz) {
    return clazz != null ? clazz.lookupClassMethod(this) : null;
  }

  @Override
  public ProgramMethod lookupOnProgramClass(DexProgramClass clazz) {
    return clazz != null ? clazz.lookupProgramMethod(this) : null;
  }

  @Override
  public String toString() {
    return toSourceString();
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
    return DexMethodSignature.create(this);
  }

  @Override
  public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
    if (collectIndexedItemsExceptName(appView, indexedItems)) {
      collectIndexedItemsName(appView, indexedItems);
    }
  }

  boolean collectIndexedItemsExceptName(AppView<?> appView, IndexedItemCollection indexedItems) {
    if (indexedItems.addMethod(this)) {
      holder.collectIndexedItems(appView, indexedItems);
      proto.collectIndexedItems(appView, indexedItems);
      return true;
    }
    return false;
  }

  void collectIndexedItemsName(AppView<?> appView, IndexedItemCollection indexedItems) {
    appView.getNamingLens().lookupName(this).collectIndexedItems(indexedItems);
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
    return holder.hashCode() * 7 + proto.hashCode() * 29 + name.hashCode() * 31;
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

  @Override
  public boolean match(DexMethod method) {
    return isIdenticalTo(method) || match(method.getProto(), method.getName());
  }

  public boolean match(DexMethodSignature method) {
    return match(method.getProto(), method.getName());
  }

  public boolean match(DexProto methodProto, DexString methodName) {
    return proto.isIdenticalTo(methodProto) && name.isIdenticalTo(methodName);
  }

  @Override
  public boolean match(DexEncodedMethod encodedMethod) {
    return match(encodedMethod.getReference());
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

  public String toSourceStringWithoutReturnType() {
    return toSourceString(true, false);
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
    return dexItemFactory.deserializeLambdaMethodName.isIdenticalTo(name)
        && dexItemFactory.deserializeLambdaMethodProto.isIdenticalTo(proto);
  }

  public boolean isInstanceInitializer(DexItemFactory factory) {
    return factory.isConstructor(this);
  }

  public boolean mustBeInlinedIntoInstanceInitializer(AppView<?> appView) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    if (getName().startsWith(dexItemFactory.temporaryConstructorMethodPrefix)) {
      DexClassAndMethod method = appView.definitionFor(this);
      return method != null
          && appView
              .graphLens()
              .getOriginalMethodSignature(this)
              .isInstanceInitializer(dexItemFactory);
    }
    return false;
  }

  public boolean isHorizontallyMergedInstanceInitializer(DexItemFactory dexItemFactory) {
    return getName().startsWith(dexItemFactory.syntheticConstructorMethodPrefix);
  }

  public boolean isInstanceInitializerInlineIntoOrMerged(AppView<?> appView) {
    return isInstanceInitializer(appView.dexItemFactory())
        || mustBeInlinedIntoInstanceInitializer(appView)
        || isHorizontallyMergedInstanceInitializer(appView.dexItemFactory());
  }

  public DexMethod withExtraArgumentPrepended(DexType type, DexItemFactory dexItemFactory) {
    return dexItemFactory.createMethod(
        holder, getProto().prependParameter(type, dexItemFactory), name);
  }

  public DexMethod withHolder(DexDefinition definition, DexItemFactory dexItemFactory) {
    return withHolder(definition.getContextType(), dexItemFactory);
  }

  @Override
  public DexMethod withHolder(DexType reference, DexItemFactory dexItemFactory) {
    return dexItemFactory.createMethod(reference.getContextType(), proto, name);
  }

  public DexMethod withName(String name, DexItemFactory dexItemFactory) {
    return withName(dexItemFactory.createString(name), dexItemFactory);
  }

  public DexMethod withName(DexString name, DexItemFactory dexItemFactory) {
    return dexItemFactory.createMethod(holder, proto, name);
  }

  public DexMethod withProto(DexProto proto, DexItemFactory dexItemFactory) {
    return dexItemFactory.createMethod(holder, proto, name);
  }
}
