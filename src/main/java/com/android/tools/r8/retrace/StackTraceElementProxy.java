// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Keep;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import java.util.Arrays;

@Keep
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

  public abstract T toRetracedItem(
      RetraceStackTraceElementProxy<T, ST> retracedProxy, boolean verbose);

  public void registerUses(
      MappingSupplier<?> mappingSupplier, DiagnosticsHandler diagnosticsHandler) {
    if (hasClassName()) {
      mappingSupplier.registerClassUse(diagnosticsHandler, getClassReference());
    }
    if (hasMethodArguments()) {
      Arrays.stream(getMethodArguments().split(","))
          .forEach(
              typeName ->
                  registerUseFromTypeReference(mappingSupplier, typeName, diagnosticsHandler));
    }
    if (hasFieldOrReturnType() && !getFieldOrReturnType().equals("void")) {
      registerUseFromTypeReference(mappingSupplier, getFieldOrReturnType(), diagnosticsHandler);
    }
  }

  private static void registerUseFromTypeReference(
      MappingSupplier<?> mappingSupplier, String typeName, DiagnosticsHandler diagnosticsHandler) {
    TypeReference typeReference = Reference.typeFromTypeName(typeName);
    if (typeReference.isArray()) {
      typeReference = typeReference.asArray().getBaseType();
    }
    if (typeReference.isClass()) {
      mappingSupplier.registerClassUse(diagnosticsHandler, typeReference.asClass());
    }
  }
}
