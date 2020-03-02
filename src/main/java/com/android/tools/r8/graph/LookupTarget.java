// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

public interface LookupTarget {
  default boolean isMethodTarget() {
    return false;
  }

  default boolean isLambdaTarget() {
    return false;
  }

  default DexClassAndMethod asMethodTarget() {
    return null;
  }

  default LookupLambdaTarget asLambdaTarget() {
    return null;
  }
}
