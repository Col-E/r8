// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.MethodReferenceUtils;
import java.util.function.Predicate;

public class ArgumentPropagatorCodeScannerResultInspector {

  private final DexItemFactory dexItemFactory;
  private final MethodStateCollectionByReference methodStates;

  public ArgumentPropagatorCodeScannerResultInspector(
      DexItemFactory dexItemFactory, MethodStateCollectionByReference methodStates) {
    this.dexItemFactory = dexItemFactory;
    this.methodStates = methodStates;
  }

  public ArgumentPropagatorCodeScannerResultInspector apply(
      ThrowableConsumer<ArgumentPropagatorCodeScannerResultInspector> consumer) {
    consumer.acceptWithRuntimeException(this);
    return this;
  }

  public ArgumentPropagatorCodeScannerResultInspector assertHasBottomMethodState(
      MethodReference methodReference) {
    return assertHasMethodStateThatMatches(
        "Expected method state for "
            + MethodReferenceUtils.toSourceString(methodReference)
            + " to be bottom",
        methodReference,
        MethodState::isBottom);
  }

  public ArgumentPropagatorCodeScannerResultInspector assertHasMonomorphicMethodState(
      MethodReference methodReference) {
    return assertHasMethodStateThatMatches(
        "Expected method state for "
            + MethodReferenceUtils.toSourceString(methodReference)
            + " to be monomorphic",
        methodReference,
        MethodState::isMonomorphic);
  }

  public ArgumentPropagatorCodeScannerResultInspector assertHasPolymorphicMethodState(
      MethodReference methodReference) {
    return assertHasMethodStateThatMatches(
        "Expected method state for "
            + MethodReferenceUtils.toSourceString(methodReference)
            + " to be polymorphic",
        methodReference,
        MethodState::isPolymorphic);
  }

  public ArgumentPropagatorCodeScannerResultInspector assertHasUnknownMethodState(
      MethodReference methodReference) {
    return assertHasMethodStateThatMatches(
        "Expected method state for "
            + MethodReferenceUtils.toSourceString(methodReference)
            + " to be unknown",
        methodReference,
        MethodState::isUnknown);
  }

  public ArgumentPropagatorCodeScannerResultInspector assertHasMethodStateThatMatches(
      String message, MethodReference methodReference, Predicate<MethodState> predicate) {
    DexMethod method = MethodReferenceUtils.toDexMethod(methodReference, dexItemFactory);
    MethodState methodState = methodStates.get(method);
    assertTrue(message + ", was: " + methodState, predicate.test(methodState));
    return this;
  }
}
