// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class ResultFormatter {

  private final StringConsumer output;
  private final DiagnosticsHandler diagnosticsHandler;

  ResultFormatter(StringConsumer output, DiagnosticsHandler diagnosticsHandler) {
    this.output = output;
    this.diagnosticsHandler = diagnosticsHandler;
  }

  protected void append(String string) {
    output.accept(string, diagnosticsHandler);
  }

  protected void appendLine(String string) {
    output.accept(string + System.lineSeparator(), diagnosticsHandler);
  }

  protected void printArguments(DexMethod method) {
    append("(");
    for (int i = 0; i < method.getArity(); i++) {
      if (i != 0) {
        append(",");
      }
      append(method.proto.parameters.values[i].toSourceString());
    }
    append(")");
  }

  protected abstract void printConstructorName(DexEncodedMethod encodedMethod);

  private void printError(String message) {
    appendLine("# Error: " + message);
  }

  protected abstract void printField(DexClass dexClass, DexField field);

  protected abstract void printMethod(DexEncodedMethod encodedMethod, String typeName);

  protected abstract void printPackageNames(List<String> packageNames);

  protected void printNameAndReturn(DexEncodedMethod encodedMethod) {
    if (encodedMethod.accessFlags.isConstructor()) {
      printConstructorName(encodedMethod);
    } else {
      DexMethod method = encodedMethod.method;
      append(method.proto.returnType.toSourceString());
      append(" ");
      append(method.name.toSourceString());
    }
  }

  protected abstract void printTypeHeader(DexClass dexClass);

  protected abstract void printTypeFooter();

  void format(Result result) {
    int errors =
        print(
            result.application,
            result.types,
            result.keepPackageNames,
            result.fields,
            result.methods,
            result.missingDefinition);
    output.finished(diagnosticsHandler);
    assert errors == result.missingDefinition.size();
  }

  private int print(
      DexApplication application,
      Set<DexType> types,
      Set<String> keepPackageNames,
      Map<DexType, Set<DexField>> fields,
      Map<DexType, Set<DexMethod>> methods,
      Set<DexReference> missingDefinition) {
    int errors = 0;
    List<DexType> sortedTypes = new ArrayList<>(types);
    sortedTypes.sort(Comparator.comparing(DexType::toSourceString));
    for (DexType type : sortedTypes) {
      DexClass dexClass = application.definitionFor(type);
      if (missingDefinition.contains(type)) {
        assert dexClass == null;
        printError("Could not find definition for type " + type.toSourceString());
        errors++;
        continue;
      }
      printTypeHeader(dexClass);
      List<DexEncodedMethod> methodDefinitions = new ArrayList<>(methods.size());
      for (DexMethod method : methods.get(type)) {
        DexEncodedMethod encodedMethod = dexClass.lookupMethod(method);
        if (missingDefinition.contains(method)) {
          assert encodedMethod == null;
          printError("Could not find definition for method " + method.toSourceString());
          errors++;
          continue;
        }
        methodDefinitions.add(encodedMethod);
      }
      methodDefinitions.sort(Comparator.comparing(x -> x.method.name.toSourceString()));
      for (DexEncodedMethod encodedMethod : methodDefinitions) {
        printMethod(encodedMethod, dexClass.type.toSourceString());
      }
      List<DexField> sortedFields = new ArrayList<>(fields.get(type));
      sortedFields.sort(Comparator.comparing(DexField::toSourceString));
      for (DexField field : sortedFields) {
        if (missingDefinition.contains(field)) {
          printError("Could not find definition for field " + field.toSourceString());
          errors++;
          continue;
        }
        printField(dexClass, field);
      }
      printTypeFooter();
    }
    ArrayList<String> packageNamesToKeep = new ArrayList<>(keepPackageNames);
    Collections.sort(packageNamesToKeep);
    printPackageNames(packageNamesToKeep);
    return errors;
  }
}
