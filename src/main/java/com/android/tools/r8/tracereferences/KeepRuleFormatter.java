// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedClass;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedField;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedMethod;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;

class KeepRuleFormatter extends Formatter {
  final boolean allowObfuscation;

  KeepRuleFormatter(boolean allowObfuscation) {
    this.allowObfuscation = allowObfuscation;
  }

  @Override
  protected void printTypeHeader(TracedClass tracedClass) {
    if (tracedClass.isMissingDefinition()) {
      appendLine("# Missing class: " + tracedClass.getReference().getTypeName());
      return;
    }
    append(allowObfuscation ? "-keep,allowobfuscation" : "-keep");
    if (tracedClass.getAccessFlags().isInterface()) {
      appendLine(" interface " + tracedClass.getReference().getTypeName() + " {");
    } else if (tracedClass.getAccessFlags().isEnum()) {
      appendLine(" enum " + tracedClass.getReference().getTypeName() + " {");
    } else {
      appendLine(" class " + tracedClass.getReference().getTypeName() + " {");
    }
  }

  @Override
  protected void printConstructorName(MethodReference method) {
    append("<init>");
  }

  @Override
  protected void printField(TracedField field) {
    append(
        "  "
            + field.getReference().getFieldType().getTypeName()
            + " "
            + field.getReference().getFieldName()
            + ";"
            + System.lineSeparator());
  }

  @Override
  protected void printMethod(TracedMethod tracedMethod) {
    if (tracedMethod.getReference().getMethodName().equals("<clinit>")) {
      return;
    }
    append("  ");
    if (tracedMethod.getAccessFlags().isPublic()) {
      append("public ");
    } else if (tracedMethod.getAccessFlags().isPrivate()) {
      append("private ");
    } else if (tracedMethod.getAccessFlags().isProtected()) {
      append("protected ");
    }
    if (tracedMethod.getAccessFlags().isStatic()) {
      append("static ");
    }
    printNameAndReturn(tracedMethod.getReference());
    printArguments(tracedMethod.getReference());
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
