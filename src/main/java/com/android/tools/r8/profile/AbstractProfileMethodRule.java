// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile;

import com.android.tools.r8.graph.DexMethod;

public interface AbstractProfileMethodRule extends AbstractProfileRule {

  DexMethod getReference();

  interface Builder<
      MethodRule extends AbstractProfileMethodRule,
      MethodRuleBuilder extends Builder<MethodRule, MethodRuleBuilder>> {

    boolean isGreaterThanOrEqualTo(MethodRuleBuilder methodRuleBuilder);

    MethodRuleBuilder join(MethodRule methodRule);

    MethodRuleBuilder join(MethodRuleBuilder methodRuleBuilder);

    MethodRuleBuilder join(MethodRuleBuilder methodRuleBuilder, Runnable onChangedHandler);

    MethodRuleBuilder setIsStartup();

    MethodRuleBuilder setMethod(DexMethod method);

    MethodRule build();
  }
}
