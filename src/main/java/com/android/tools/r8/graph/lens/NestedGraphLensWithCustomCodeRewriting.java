// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.lens;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.optimize.CustomLensCodeRewriter;
import com.android.tools.r8.utils.collections.BidirectionalManyToManyRepresentativeMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import java.util.Map;

public class NestedGraphLensWithCustomCodeRewriting extends NestedGraphLens {

  private final CustomLensCodeRewriter customLensCodeRewriter;

  public NestedGraphLensWithCustomCodeRewriting(
      AppView<?> appView,
      BidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap,
      BidirectionalManyToOneRepresentativeMap<DexMethod, DexMethod> methodMap,
      BidirectionalManyToManyRepresentativeMap<DexType, DexType> typeMap,
      CustomLensCodeRewriter customLensCodeRewriter) {
    super(appView, fieldMap, methodMap, typeMap);
    this.customLensCodeRewriter = customLensCodeRewriter;
  }

  public NestedGraphLensWithCustomCodeRewriting(
      AppView<?> appView,
      BidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap,
      Map<DexMethod, DexMethod> methodMap,
      BidirectionalManyToManyRepresentativeMap<DexType, DexType> typeMap,
      CustomLensCodeRewriter customLensCodeRewriter,
      BidirectionalManyToManyRepresentativeMap<DexMethod, DexMethod> newMethodSignatures) {
    super(appView, fieldMap, methodMap, typeMap, newMethodSignatures);
    this.customLensCodeRewriter = customLensCodeRewriter;
  }

  @Override
  public boolean hasCustomCodeRewritings() {
    return true;
  }

  @Override
  public CustomLensCodeRewriter getCustomCodeRewriting() {
    return customLensCodeRewriter;
  }
}
