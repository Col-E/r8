// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.naming.NamingLens;
import java.util.function.Consumer;

public abstract class ArtProfileRule {

  public abstract void accept(
      Consumer<ArtProfileClassRule> classRuleConsumer,
      Consumer<ArtProfileMethodRule> methodRuleConsumer);

  public boolean isClassRule() {
    return false;
  }

  public ArtProfileClassRule asClassRule() {
    return null;
  }

  public boolean isMethodRule() {
    return false;
  }

  public ArtProfileMethodRule asMethodRule() {
    return null;
  }

  public abstract ArtProfileRule rewrittenWithLens(GraphLens lens);

  public abstract ArtProfileRule rewrittenWithLens(
      DexItemFactory dexItemFactory, NamingLens namingLens);
}
