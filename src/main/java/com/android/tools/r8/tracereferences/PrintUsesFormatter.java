// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import java.util.List;

class PrintUsesFormatter extends ResultFormatter {

  PrintUsesFormatter(StringConsumer output, DiagnosticsHandler diagnosticsHandler) {
    super(output, diagnosticsHandler);
  }

  @Override
  protected void printConstructorName(DexEncodedMethod encodedMethod) {
    if (encodedMethod.accessFlags.isStatic()) {
      append("<clinit>");
    } else {
      String holderName = encodedMethod.holder().toSourceString();
      String constructorName = holderName.substring(holderName.lastIndexOf('.') + 1);
      append(constructorName);
    }
  }

  @Override
  protected void printMethod(DexEncodedMethod encodedMethod, String typeName) {
    append(typeName + ": ");
    printNameAndReturn(encodedMethod);
    printArguments(encodedMethod.method);
    appendLine("");
  }

  @Override
  protected void printPackageNames(List<String> packageNames) {
    // No need to print package names for text output.
  }

  @Override
  protected void printTypeHeader(DexClass dexClass) {
    appendLine(dexClass.type.toSourceString());
  }

  @Override
  protected void printTypeFooter() {}

  @Override
  protected void printField(DexClass dexClass, DexField field) {
    appendLine(
        dexClass.type.toSourceString()
            + ": "
            + field.type.toSourceString()
            + " "
            + field.name.toString());
  }
}
