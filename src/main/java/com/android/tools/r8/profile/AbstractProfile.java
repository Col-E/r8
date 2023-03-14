// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.ThrowingConsumer;

public interface AbstractProfile<
    ClassRule extends AbstractProfileClassRule, MethodRule extends AbstractProfileMethodRule> {

  boolean containsClassRule(DexType type);

  boolean containsMethodRule(DexMethod method);

  <E1 extends Exception, E2 extends Exception> void forEachRule(
      ThrowingConsumer<ClassRule, E1> classRuleConsumer,
      ThrowingConsumer<MethodRule, E2> methodRuleConsumer)
      throws E1, E2;

  ClassRule getClassRule(DexType type);

  MethodRule getMethodRule(DexMethod method);

  interface Builder<
      ClassRule extends AbstractProfileClassRule,
      MethodRule extends AbstractProfileMethodRule,
      Profile extends AbstractProfile<ClassRule, MethodRule>,
      ProfileBuilder extends Builder<ClassRule, MethodRule, Profile, ProfileBuilder>> {

    ProfileBuilder addRule(AbstractProfileRule rule);

    ProfileBuilder addClassRule(ClassRule classRule);

    ProfileBuilder addMethodRule(MethodRule methodRule);

    Profile build();
  }
}
