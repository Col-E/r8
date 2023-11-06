// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.google.common.base.Splitter;
import java.util.ArrayList;
import java.util.List;

@KeepForApi
public abstract class StackTraceElementProxy<T, ST extends StackTraceElementProxy<T, ST>> {

  public abstract boolean hasClassName();

  public abstract boolean hasMethodName();

  public abstract boolean hasSourceFile();

  public abstract boolean hasLineNumber();

  public abstract boolean hasFieldName();

  public abstract boolean hasFieldOrReturnType();

  public abstract boolean hasMethodArguments();

  public abstract ClassReference getClassReference();

  public abstract String getMethodName();

  public abstract String getSourceFile();

  public abstract int getLineNumber();

  public abstract String getFieldName();

  public abstract String getFieldOrReturnType();

  public abstract String getMethodArguments();

  public List<TypeReference> getMethodArgumentTypeReferences() {
    if (!hasMethodArguments()) {
      return null;
    }
    List<TypeReference> typeReferences = new ArrayList<>();
    for (String typeName : Splitter.onPattern(",\\s*").split(getMethodArguments())) {
      typeReferences.add(Reference.typeFromTypeName(typeName));
    }
    return typeReferences;
  }

  public abstract T toRetracedItem(
      RetraceStackTraceElementProxy<T, ST> retracedProxy, boolean verbose);

  public void registerUses(
      MappingSupplierBase<?> mappingSupplier, DiagnosticsHandler diagnosticsHandler) {
    if (hasClassName()) {
      mappingSupplier.registerClassUse(diagnosticsHandler, getClassReference());
    }
    if (hasMethodArguments()) {
      getMethodArgumentTypeReferences()
          .forEach(
              typeReference ->
                  registerUseFromTypeReference(mappingSupplier, typeReference, diagnosticsHandler));
    }
    if (hasFieldOrReturnType() && !getFieldOrReturnType().equals("void")) {
      registerUseFromTypeReference(
          mappingSupplier, Reference.typeFromTypeName(getFieldOrReturnType()), diagnosticsHandler);
    }
  }

  private static void registerUseFromTypeReference(
      MappingSupplierBase<?> mappingSupplier,
      TypeReference typeReference,
      DiagnosticsHandler diagnosticsHandler) {
    if (typeReference.isArray()) {
      typeReference = typeReference.asArray().getBaseType();
    }
    if (typeReference.isClass()) {
      mappingSupplier.registerClassUse(diagnosticsHandler, typeReference.asClass());
    }
  }
}
