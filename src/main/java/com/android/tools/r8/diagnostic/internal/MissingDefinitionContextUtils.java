// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic.internal;

import com.android.tools.r8.diagnostic.MissingDefinitionContext;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.Definition;
import com.android.tools.r8.graph.ProgramDerivedContext;

public class MissingDefinitionContextUtils {

  public static MissingDefinitionContext create(ProgramDerivedContext programDerivedContext) {
    Definition context = programDerivedContext.getContext();
    MissingDefinitionContextBase.Builder<?> builder;
    if (context.isClass()) {
      builder =
          MissingDefinitionClassContext.builder()
              .setClassContext(context.asClass().getClassReference());
    } else if (context.isField()) {
      builder =
          MissingDefinitionFieldContext.builder()
              .setFieldContext(context.asField().getFieldReference());
    } else if (context.isMethod()) {
      builder =
          MissingDefinitionMethodContext.builder()
              .setMethodContext(context.asMethod().getMethodReference());
    } else {
      throw new Unreachable();
    }
    return builder.setOrigin(context.getOrigin()).build();
  }
}
