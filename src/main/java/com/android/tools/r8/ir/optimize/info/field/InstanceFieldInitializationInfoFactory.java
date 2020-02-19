// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.field;

import com.android.tools.r8.ir.analysis.type.ClassTypeLatticeElement;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import java.util.concurrent.ConcurrentHashMap;

public class InstanceFieldInitializationInfoFactory {

  private ConcurrentHashMap<Integer, InstanceFieldArgumentInitializationInfo>
      argumentInitializationInfos = new ConcurrentHashMap<>();

  public InstanceFieldArgumentInitializationInfo createArgumentInitializationInfo(
      int argumentIndex) {
    return argumentInitializationInfos.computeIfAbsent(
        argumentIndex, InstanceFieldArgumentInitializationInfo::new);
  }

  public InstanceFieldTypeInitializationInfo createTypeInitializationInfo(
      ClassTypeLatticeElement dynamicLowerBoundType, TypeLatticeElement dynamicUpperBoundType) {
    return new InstanceFieldTypeInitializationInfo(dynamicLowerBoundType, dynamicUpperBoundType);
  }
}
