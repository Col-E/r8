// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.startup.profile;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.Timing;

public class EmptyStartupProfile extends StartupProfile {

  EmptyStartupProfile() {}

  @Override
  public boolean containsClassRule(DexType type) {
    return false;
  }

  @Override
  public boolean containsMethodRule(DexMethod method) {
    return false;
  }

  @Override
  public <E extends Exception> void forEachRule(
      ThrowingConsumer<? super StartupProfileRule, E> consumer) {
    // Intentionally empty.
  }

  @Override
  public <E1 extends Exception, E2 extends Exception> void forEachRule(
      ThrowingConsumer<? super StartupProfileClassRule, E1> classRuleConsumer,
      ThrowingConsumer<? super StartupProfileMethodRule, E2> methodRuleConsumer) {
    // Intentionally empty.
  }

  @Override
  public StartupProfileClassRule getClassRule(DexType type) {
    return null;
  }

  @Override
  public StartupProfileMethodRule getMethodRule(DexMethod method) {
    return null;
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public boolean isStartupClass(DexType type) {
    return false;
  }

  @Override
  public EmptyStartupProfile rewrittenWithLens(GraphLens graphLens, Timing timing) {
    return this;
  }

  @Override
  public EmptyStartupProfile toStartupProfileForWriting(AppView<?> appView) {
    return this;
  }

  @Override
  public StartupProfile withoutMissingItems(AppView<?> appView) {
    return this;
  }

  @Override
  public EmptyStartupProfile withoutPrunedItems(
      PrunedItems prunedItems, SyntheticItems syntheticItems) {
    return this;
  }
}
