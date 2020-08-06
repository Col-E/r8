// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.GraphLens;
import com.google.common.collect.Sets;
import java.util.Set;

public class LensUtils {

  public static Set<DexEncodedMethod> rewrittenWithRenamedSignature(
      Set<DexEncodedMethod> methods, DexDefinitionSupplier definitions, GraphLens lens) {
    Set<DexEncodedMethod> result = Sets.newIdentityHashSet();
    for (DexEncodedMethod method : methods) {
      result.add(lens.mapDexEncodedMethod(method, definitions));
    }
    return result;
  }
}
