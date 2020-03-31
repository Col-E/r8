// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import java.util.List;

public interface KmDeclarationContainerSubject {
  List<String> getParameterTypeDescriptorsInFunctions();

  List<String> getReturnTypeDescriptorsInFunctions();

  List<String> getReturnTypeDescriptorsInProperties();

  KmFunctionSubject kmFunctionWithUniqueName(String name);

  KmFunctionSubject kmFunctionExtensionWithUniqueName(String name);

  List<KmFunctionSubject> getFunctions();

  List<ClassSubject> getParameterTypesInFunctions();

  List<ClassSubject> getReturnTypesInFunctions();

  KmPropertySubject kmPropertyWithUniqueName(String name);

  KmPropertySubject kmPropertyExtensionWithUniqueName(String name);

  List<KmPropertySubject> getProperties();

  List<ClassSubject> getReturnTypesInProperties();

  List<KmTypeAliasSubject> getTypeAliases();

  KmTypeAliasSubject kmTypeAliasWithUniqueName(String name);
}
