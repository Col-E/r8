// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cfmethodgeneration;

import java.util.LinkedHashSet;
import java.util.Set;

public class CfCodeGeneratorImportCollection {

  private final Set<String> imports = new LinkedHashSet<>();

  String generateImports() {
    StringBuilder builder = new StringBuilder();
    for (String className : imports) {
      builder.append("import ").append(className).append(";").append('\n');
    }
    return builder.toString();
  }

  String getCfVersion() {
    return getR8ClassName("cf", "CfVersion");
  }

  String getClassAccessFlags() {
    return getR8ClassName("graph", "ClassAccessFlags");
  }

  String getClassSignature() {
    return getR8ClassName("graph.GenericSignature", "ClassSignature");
  }

  String getComputedApiLevel() {
    return getR8ClassName("androidapi", "ComputedApiLevel");
  }

  String getDexAnnotationSet() {
    return getR8ClassName("graph", "DexAnnotationSet");
  }

  String getDexEncodedField() {
    return getR8ClassName("graph", "DexEncodedField");
  }

  String getDexEncodedMethod() {
    return getR8ClassName("graph", "DexEncodedMethod");
  }

  String getDexItemFactory() {
    return getR8ClassName("graph", "DexItemFactory");
  }

  String getDexProgramClass() {
    return getR8ClassName("graph", "DexProgramClass");
  }

  String getDexTypeList() {
    return getR8ClassName("graph", "DexTypeList");
  }

  String getEnclosingMethodAttribute() {
    return getR8ClassName("graph", "EnclosingMethodAttribute");
  }

  String getFieldAccessFlags() {
    return getR8ClassName("graph", "FieldAccessFlags");
  }

  String getFieldTypeSignature() {
    return getR8ClassName("graph.GenericSignature", "FieldTypeSignature");
  }

  String getJavaUtilCollections() {
    addImport("java.util.Collections");
    return "Collections";
  }

  String getMethodAccessFlags() {
    return getR8ClassName("graph", "MethodAccessFlags");
  }

  String getMethodCollectionFactory() {
    return getR8ClassName("graph.MethodCollection", "MethodCollectionFactory");
  }

  String getNestHostClassAttribute() {
    return getR8ClassName("graph", "NestHostClassAttribute");
  }

  String getOrigin() {
    return getR8ClassName("origin", "Origin");
  }

  String getProgramResourceKind() {
    return getR8ClassName("ProgramResource", "Kind");
  }

  private String getR8ClassName(String context, String name) {
    String canonicalName =
        "com.android.tools.r8." + (context != null ? (context + ".") : "") + name;
    addImport(canonicalName);
    return name;
  }

  String addImport(String name) {
    imports.add(name);
    return name;
  }
}
