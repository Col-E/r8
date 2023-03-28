// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;

public interface Rewritable<R extends Rewritable<R>> {

  DexType getHolder();

  R rewrite(NonIdentityGraphLens lens);
}
