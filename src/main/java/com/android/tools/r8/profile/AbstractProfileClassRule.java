// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile;

import com.android.tools.r8.graph.DexType;

public interface AbstractProfileClassRule extends AbstractProfileRule {

  DexType getReference();

  interface Builder<ClassRule extends AbstractProfileClassRule> {

    ClassRule build();
  }
}
