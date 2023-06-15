// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.lens;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.utils.ThrowingAction;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public abstract class NonIdentityGraphLens extends GraphLens {

  private final DexItemFactory dexItemFactory;
  private GraphLens previousLens;

  private final Map<DexType, DexType> arrayTypeCache = new ConcurrentHashMap<>();

  public NonIdentityGraphLens(AppView<?> appView) {
    this(appView.dexItemFactory(), appView.graphLens());
  }

  public NonIdentityGraphLens(DexItemFactory dexItemFactory, GraphLens previousLens) {
    this.dexItemFactory = dexItemFactory;
    this.previousLens = previousLens;
  }

  public final DexItemFactory dexItemFactory() {
    return dexItemFactory;
  }

  public final GraphLens getPrevious() {
    return previousLens;
  }

  @SuppressWarnings("unchecked")
  public final <T extends com.android.tools.r8.graph.lens.NonIdentityGraphLens> T find(
      Predicate<com.android.tools.r8.graph.lens.NonIdentityGraphLens> predicate) {
    GraphLens current = this;
    while (current.isNonIdentityLens()) {
      com.android.tools.r8.graph.lens.NonIdentityGraphLens nonIdentityGraphLens =
          current.asNonIdentityLens();
      if (predicate.test(nonIdentityGraphLens)) {
        return (T) nonIdentityGraphLens;
      }
      current = nonIdentityGraphLens.getPrevious();
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  public final <T extends com.android.tools.r8.graph.lens.NonIdentityGraphLens> T findPrevious(
      Predicate<com.android.tools.r8.graph.lens.NonIdentityGraphLens> predicate) {
    GraphLens previous = getPrevious();
    return previous.isNonIdentityLens() ? previous.asNonIdentityLens().find(predicate) : null;
  }

  public final <T extends com.android.tools.r8.graph.lens.NonIdentityGraphLens> T findPreviousUntil(
      Predicate<com.android.tools.r8.graph.lens.NonIdentityGraphLens> predicate,
      Predicate<com.android.tools.r8.graph.lens.NonIdentityGraphLens> stoppingCriterion) {
    T found = findPrevious(predicate.or(stoppingCriterion));
    return (found == null || stoppingCriterion.test(found)) ? null : found;
  }

  public final <E extends Exception> void withAlternativeParentLens(
      GraphLens lens, ThrowingAction<E> action) throws E {
    GraphLens oldParent = getPrevious();
    previousLens = lens;
    action.execute();
    previousLens = oldParent;
  }

  @Override
  public MethodLookupResult lookupMethod(
      DexMethod method, DexMethod context, InvokeType type, GraphLens codeLens) {
    if (method.getHolderType().isArrayType()) {
      assert lookupType(method.getReturnType()) == method.getReturnType();
      assert method.getParameters().stream()
          .allMatch(parameterType -> lookupType(parameterType) == parameterType);
      return MethodLookupResult.builder(this)
          .setReference(method.withHolder(lookupType(method.getHolderType()), dexItemFactory))
          .setType(type)
          .build();
    }
    assert method.getHolderType().isClassType();
    return internalLookupMethod(method, context, type, codeLens, result -> result);
  }

  @Override
  public String lookupPackageName(String pkg) {
    return getPrevious().lookupPackageName(pkg);
  }

  @Override
  public final DexType lookupType(DexType type, GraphLens appliedLens) {
    if (type.isClassType()) {
      return lookupClassType(type, appliedLens);
    }
    if (type.isArrayType()) {
      DexType result = arrayTypeCache.get(type);
      if (result == null) {
        DexType baseType = type.toBaseType(dexItemFactory);
        DexType newType = lookupType(baseType, appliedLens);
        result = baseType == newType ? type : type.replaceBaseType(newType, dexItemFactory);
        arrayTypeCache.put(type, result);
      }
      return result;
    }
    assert type.isNullValueType() || type.isPrimitiveType() || type.isVoidType();
    return type;
  }

  @Override
  protected FieldLookupResult internalLookupField(
      DexField reference, GraphLens codeLens, LookupFieldContinuation continuation) {
    if (this == codeLens) {
      return getIdentityLens().internalLookupField(reference, codeLens, continuation);
    }
    return previousLens.internalLookupField(
        reference,
        codeLens,
        previous -> continuation.lookupField(internalDescribeLookupField(previous)));
  }

  @Override
  protected MethodLookupResult internalLookupMethod(
      DexMethod reference,
      DexMethod context,
      InvokeType type,
      GraphLens codeLens,
      LookupMethodContinuation continuation) {
    if (this == codeLens) {
      GraphLens identityLens = getIdentityLens();
      return identityLens.internalLookupMethod(
          reference, context, type, identityLens, continuation);
    }
    return previousLens.internalLookupMethod(
        reference,
        getPreviousMethodSignature(context),
        type,
        codeLens,
        previous ->
            continuation.lookupMethod(internalDescribeLookupMethod(previous, context, codeLens)));
  }

  protected abstract FieldLookupResult internalDescribeLookupField(FieldLookupResult previous);

  /**
   * The codeLens is only needed for assertions that call other lens methods, it should not
   * influence the lookup itself.
   */
  protected abstract MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context, GraphLens codeLens);

  protected abstract DexType getNextClassType(DexType type);

  public abstract DexField getPreviousFieldSignature(DexField field);

  public abstract DexMethod getPreviousMethodSignature(DexMethod method);

  public abstract DexType getPreviousClassType(DexType type);

  /***
   * The previous mapping for a method often coincides with the previous method signature, but it
   * may not, for example for bridges inserted in vertically merged classes where the original
   * signature is used for computing invoke-super but should not be used for mapping output.
   */
  public DexMethod getPreviousMethodSignatureForMapping(DexMethod method) {
    return getPreviousMethodSignature(method);
  }

  public abstract DexField getNextFieldSignature(DexField field);

  public abstract DexMethod getNextMethodSignature(DexMethod method);

  @Override
  public final boolean isIdentityLens() {
    return false;
  }

  @Override
  public boolean isIdentityLensForFields(GraphLens codeLens) {
    return this == codeLens;
  }

  @Override
  public final boolean isNonIdentityLens() {
    return true;
  }

  @Override
  public final com.android.tools.r8.graph.lens.NonIdentityGraphLens asNonIdentityLens() {
    return this;
  }
}
