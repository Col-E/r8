// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedClass;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedField;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedMethod;
import java.util.List;

class PrintUsesFormatter extends Formatter {

  @Override
  protected void printConstructorName(MethodReference method) {
    if (method.getMethodName().equals("<clinit>")) {
      append("<clinit>");
    } else {
      String holderName = method.getHolderClass().getTypeName();
      String constructorName = holderName.substring(holderName.lastIndexOf('.') + 1);
      append(constructorName);
    }
  }

  @Override
  protected void printMethod(TracedMethod method) {
    append(method.getReference().getHolderClass().getTypeName() + ": ");
    printNameAndReturn(method.getReference());
    printArguments(method.getReference());
    appendLine("");
  }

  @Override
  protected void printPackageNames(List<String> packageNames) {
    // No need to print package names for text output.
  }

  @Override
  protected void printTypeHeader(TracedClass type) {
    appendLine(type.getReference().getTypeName());
  }

  @Override
  protected void printTypeFooter() {}

  @Override
  protected void printField(TracedField field) {
    appendLine(
        field.getReference().getHolderClass().getTypeName()
            + ": "
            + field.getReference().getFieldType().getTypeName()
            + " "
            + field.getReference().getFieldName());
  }
}
