// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;

class KeepRuleFormatter extends ResultFormatter {
  final boolean allowObfuscation;

  KeepRuleFormatter(
      StringConsumer output, DiagnosticsHandler diagnosticsHandler, boolean allowObfuscation) {
    super(output, diagnosticsHandler);
    this.allowObfuscation = allowObfuscation;
  }

  @Override
  protected void printTypeHeader(DexClass dexClass) {
    append(allowObfuscation ? "-keep,allowobfuscation" : "-keep");
    if (dexClass.isInterface()) {
      append(" interface " + dexClass.type.toSourceString() + " {" + System.lineSeparator());
    } else if (dexClass.accessFlags.isEnum()) {
      append(" enum " + dexClass.type.toSourceString() + " {" + System.lineSeparator());
    } else {
      append(" class " + dexClass.type.toSourceString() + " {" + System.lineSeparator());
    }
  }

  @Override
  protected void printConstructorName(DexEncodedMethod encodedMethod) {
    append("<init>");
  }

  @Override
  protected void printField(DexClass dexClass, DexField field) {
    append(
        "  "
            + field.type.toSourceString()
            + " "
            + field.name.toString()
            + ";"
            + System.lineSeparator());
  }

  @Override
  protected void printMethod(DexEncodedMethod encodedMethod, String typeName) {
    // Static initializers do not require keep rules - it is kept by keeping the class.
    if (encodedMethod.accessFlags.isConstructor() && encodedMethod.accessFlags.isStatic()) {
      return;
    }
    append("  ");
    if (encodedMethod.isPublicMethod()) {
      append("public ");
    } else if (encodedMethod.isPrivateMethod()) {
      append("private ");
    } else if (encodedMethod.isProtectedMethod()) {
      append("protected ");
    }
    if (encodedMethod.isStatic()) {
      append("static ");
    }
    printNameAndReturn(encodedMethod);
    printArguments(encodedMethod.method);
    appendLine(";");
  }

  @Override
  protected void printPackageNames(List<String> packageNames) {
    if (!packageNames.isEmpty()) {
      append("-keeppackagenames " + StringUtils.join(packageNames, ",") + System.lineSeparator());
    }
  }

  @Override
  protected void printTypeFooter() {
    appendLine("}");
  }
}
