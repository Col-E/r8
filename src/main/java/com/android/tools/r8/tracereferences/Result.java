// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import java.util.Map;
import java.util.Set;

class Result {
  final DexApplication application;
  final Set<DexType> types;
  final Set<String> keepPackageNames;
  final Map<DexType, Set<DexField>> fields;
  final Map<DexType, Set<DexMethod>> methods;
  final Set<DexReference> missingDefinition;

  Result(
      DexApplication application,
      Set<DexType> types,
      Set<String> keepPackageNames,
      Map<DexType, Set<DexField>> fields,
      Map<DexType, Set<DexMethod>> methods,
      Set<DexReference> missingDefinition) {
    this.application = application;
    this.types = types;
    this.keepPackageNames = keepPackageNames;
    this.fields = fields;
    this.methods = methods;
    this.missingDefinition = missingDefinition;
  }
}
