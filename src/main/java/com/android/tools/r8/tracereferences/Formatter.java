// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.PackageReference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedClass;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedField;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer.TracedMethod;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

abstract class Formatter {

  private final StringBuilder output;

  Formatter() {
    output = new StringBuilder();
  }

  String get() {
    return output.toString();
  }

  protected void append(String string) {
    output.append(string);
  }

  protected void appendLine(String string) {
    output.append(StringUtils.lines(string));
  }

  protected void printArguments(MethodReference method) {
    StringUtils.append(
        output,
        ListUtils.map(method.getFormalTypes(), TypeReference::getTypeName),
        ",",
        BraceType.PARENS);
  }

  protected abstract void printConstructorName(MethodReference method);

  protected abstract void printField(TracedField field);

  protected abstract void printMethod(TracedMethod method);

  protected abstract void printPackageNames(List<String> packageNames);

  protected void printReturn(MethodReference method) {
    append(method.getReturnType() != null ? method.getReturnType().getTypeName() : "void");
  }

  protected void printNameAndReturn(MethodReference method) {
    if (method.getMethodName().equals("<init>")) {
      printConstructorName(method);
    } else {
      printReturn(method);
      append(" ");
      append(method.getMethodName());
    }
  }

  protected abstract void printTypeHeader(TracedClass clazz);

  protected abstract void printTypeFooter();

  void format(TraceReferencesResult result) {
    print(
        result.types,
        result.keepPackageNames,
        result.fields,
        result.methods,
        result.missingDefinition);
  }

  private void print(
      Set<TracedClass> types,
      Set<PackageReference> keepPackageNames,
      Map<ClassReference, Set<TracedField>> fields,
      Map<ClassReference, Set<TracedMethod>> methods,
      Set<Object> missingDefinition) {
    List<TracedClass> sortedTypes = new ArrayList<>(types);
    sortedTypes.sort(Comparator.comparing(tracedClass -> tracedClass.getReference().getTypeName()));
    for (TracedClass type : sortedTypes) {
      Set<TracedMethod> methodsForClass =
          methods.getOrDefault(type.getReference(), Collections.emptySet());
      Set<TracedField> fieldsForClass =
          fields.getOrDefault(type.getReference(), Collections.emptySet());
      if (missingDefinition.contains(type.getReference())) {
        continue;
      }
      printTypeHeader(type);
      List<TracedMethod> sortedMethods = new ArrayList<>(methodsForClass.size());
      for (TracedMethod method : methodsForClass) {
        if (method.isMissingDefinition()) {
          continue;
        }
        assert method.getAccessFlags() != null;
        sortedMethods.add(method);
      }
      sortedMethods.sort(
          Comparator.comparing(tracedMethod -> tracedMethod.getReference().toString()));
      for (TracedMethod method : sortedMethods) {
        printMethod(method);
      }
      List<TracedField> sortedFields = new ArrayList<>(fieldsForClass);
      sortedFields.sort(Comparator.comparing(tracedField -> tracedField.getReference().toString()));
      for (TracedField field : sortedFields) {
        if (field.isMissingDefinition()) {
          continue;
        }
        printField(field);
      }
      printTypeFooter();
    }
    List<String> packageNamesToKeep =
        keepPackageNames.stream()
            .map(PackageReference::getPackageName)
            .sorted()
            .collect(Collectors.toList());
    printPackageNames(packageNamesToKeep);
  }
}
