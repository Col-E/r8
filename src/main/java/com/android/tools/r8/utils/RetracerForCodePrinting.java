// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.IndexedDexItem;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.retrace.RetraceClassElement;
import com.android.tools.r8.retrace.RetraceElement;
import com.android.tools.r8.retrace.RetraceFieldResult;
import com.android.tools.r8.retrace.RetraceMethodResult;
import com.android.tools.r8.retrace.RetraceResult;
import com.android.tools.r8.retrace.RetracedFieldReference;
import com.android.tools.r8.retrace.RetracedFieldReference.KnownRetracedFieldReference;
import com.android.tools.r8.retrace.RetracedMethodReference;
import com.android.tools.r8.retrace.RetracedMethodReference.KnownRetracedMethodReference;
import com.android.tools.r8.retrace.Retracer;
import com.android.tools.r8.retrace.internal.MappingSupplierInternalImpl;
import com.android.tools.r8.retrace.internal.RetracerImpl;
import java.util.function.Function;

public class RetracerForCodePrinting {

  private static final RetracerForCodePrinting EMPTY = new RetracerForCodePrinting(null);

  public static RetracerForCodePrinting empty() {
    return EMPTY;
  }

  private final Retracer retracer;

  private RetracerForCodePrinting(Retracer retracer) {
    this.retracer = retracer;
  }

  public static RetracerForCodePrinting create(
      ClassNameMapper classNameMapper, DiagnosticsHandler handler) {
    return classNameMapper == null
        ? empty()
        : new RetracerForCodePrinting(
            RetracerImpl.createInternal(
                MappingSupplierInternalImpl.createInternal(classNameMapper), handler));
  }

  public <T extends RetraceElement<?>> String joinAmbiguousResults(
      RetraceResult<T> retraceResult, Function<T, String> nameToString) {
    return StringUtils.join(" <OR> ", retraceResult.stream(), nameToString);
  }

  private String typeToString(
      DexType type,
      Function<DexType, String> noRetraceString,
      Function<RetraceClassElement, String> retraceResult) {
    return retracer == null
        ? noRetraceString.apply(type)
        : joinAmbiguousResults(retracer.retraceClass(type.asClassReference()), retraceResult);
  }

  public String toSourceString(DexType type) {
    return typeToString(
        type, DexType::toSourceString, element -> element.getRetracedClass().getTypeName());
  }

  public String toDescriptor(DexType type) {
    return typeToString(
        type, DexType::toDescriptorString, element -> element.getRetracedClass().getDescriptor());
  }

  private String retraceMethodToString(
      DexMethod method,
      Function<DexMethod, String> noRetraceString,
      Function<KnownRetracedMethodReference, String> knownToString,
      Function<RetracedMethodReference, String> unknownToString) {
    if (retracer == null) {
      return noRetraceString.apply(method);
    }
    RetraceMethodResult retraceMethodResult = retracer.retraceMethod(method.asMethodReference());
    return joinAmbiguousResults(
        retraceMethodResult,
        element -> {
          if (element.isUnknown()) {
            return unknownToString.apply(element.getRetracedMethod());
          } else {
            return knownToString.apply(element.getRetracedMethod().asKnown());
          }
        });
  }

  public String toSourceString(DexMethod method) {
    return retraceMethodToString(
        method,
        DexMethod::toSourceString,
        knownRetracedMethodReference ->
            knownRetracedMethodReference.getMethodReference().toSourceString(),
        unknown -> unknown.getHolderClass().getTypeName() + " " + unknown.getMethodName());
  }

  public String toDescriptor(DexMethod method) {
    return retraceMethodToString(
        method,
        m -> m.asMethodReference().toString(),
        knownRetracedMethodReference ->
            knownRetracedMethodReference.getMethodReference().toString(),
        unknown -> unknown.getHolderClass().getDescriptor() + unknown.getMethodName());
  }

  private String retraceFieldToString(
      DexField field,
      Function<DexField, String> noRetraceString,
      Function<KnownRetracedFieldReference, String> knownToString,
      Function<RetracedFieldReference, String> unknownToString) {
    if (retracer == null) {
      return noRetraceString.apply(field);
    }
    FieldReference fieldReference = field.asFieldReference();
    RetraceFieldResult retraceFieldResult = retracer.retraceField(fieldReference);
    if (retraceFieldResult.isEmpty()) {
      retraceFieldResult =
          retracer
              .retraceClass(fieldReference.getHolderClass())
              .lookupField(fieldReference.getFieldName());
    }
    return joinAmbiguousResults(
        retraceFieldResult,
        element -> {
          if (element.isUnknown()) {
            return unknownToString.apply(element.getField());
          } else {
            return knownToString.apply(element.getField().asKnown());
          }
        });
  }

  public String toSourceString(DexField field) {
    return retraceFieldToString(
        field,
        f -> f.asFieldReference().toSourceString(),
        known -> known.getFieldReference().toSourceString(),
        unknown -> unknown.getHolderClass().getDescriptor() + " " + unknown.getFieldName());
  }

  public String toDescriptor(DexField field) {
    return retraceFieldToString(
        field,
        f -> f.asFieldReference().toString(),
        known -> known.getFieldReference().toString(),
        unknown -> unknown.getHolderClass().getDescriptor() + unknown.getFieldName());
  }

  public String toDescriptor(IndexedDexItem item) {
    if (!(item instanceof DexReference)) {
      return item.toString();
    }
    return ((DexReference) item).apply(this::toDescriptor, this::toDescriptor, this::toDescriptor);
  }

  public String toSourceString(IndexedDexItem item) {
    if (!(item instanceof DexReference)) {
      return item.toSourceString();
    }
    return ((DexReference) item)
        .apply(this::toSourceString, this::toSourceString, this::toSourceString);
  }

  public boolean isEmpty() {
    return this == EMPTY;
  }
}
