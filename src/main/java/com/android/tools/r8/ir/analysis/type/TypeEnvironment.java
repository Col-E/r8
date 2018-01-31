// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Value;

public interface TypeEnvironment {
  TypeLatticeElement getLatticeElement(Value value);
  DexType getObjectType(Value value);
}
