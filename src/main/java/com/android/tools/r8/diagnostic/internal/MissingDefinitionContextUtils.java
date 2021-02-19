// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic.internal;

import com.android.tools.r8.diagnostic.MissingDefinitionClassContext;
import com.android.tools.r8.diagnostic.MissingDefinitionContext;
import com.android.tools.r8.diagnostic.MissingDefinitionFieldContext;
import com.android.tools.r8.diagnostic.MissingDefinitionMethodContext;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.Definition;
import com.android.tools.r8.graph.ProgramDerivedContext;
import com.android.tools.r8.utils.FieldReferenceUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import java.util.function.Consumer;
import java.util.function.Function;

public class MissingDefinitionContextUtils {

  public static void accept(
      MissingDefinitionContext missingDefinitionContext,
      Consumer<MissingDefinitionClassContext> missingDefinitionClassContextConsumer,
      Consumer<MissingDefinitionFieldContext> missingDefinitionFieldContextConsumer,
      Consumer<MissingDefinitionMethodContext> missingDefinitionMethodContextConsumer) {
    if (missingDefinitionContext.isClassContext()) {
      missingDefinitionClassContextConsumer.accept(missingDefinitionContext.asClassContext());
    } else if (missingDefinitionContext.isFieldContext()) {
      missingDefinitionFieldContextConsumer.accept(missingDefinitionContext.asFieldContext());
    } else {
      assert missingDefinitionContext.isMethodContext();
      missingDefinitionMethodContextConsumer.accept(missingDefinitionContext.asMethodContext());
    }
  }

  public static <T> T apply(
      MissingDefinitionContext missingDefinitionContext,
      Function<MissingDefinitionClassContext, T> missingDefinitionClassContextFn,
      Function<MissingDefinitionFieldContext, T> missingDefinitionFieldContextFn,
      Function<MissingDefinitionMethodContext, T> missingDefinitionMethodContextFn) {
    if (missingDefinitionContext.isClassContext()) {
      return missingDefinitionClassContextFn.apply(missingDefinitionContext.asClassContext());
    } else if (missingDefinitionContext.isFieldContext()) {
      return missingDefinitionFieldContextFn.apply(missingDefinitionContext.asFieldContext());
    } else {
      assert missingDefinitionContext.isMethodContext();
      return missingDefinitionMethodContextFn.apply(missingDefinitionContext.asMethodContext());
    }
  }

  public static MissingDefinitionContext create(ProgramDerivedContext programDerivedContext) {
    Definition context = programDerivedContext.getContext();
    MissingDefinitionContextBase.Builder<?> builder;
    if (context.isClass()) {
      builder =
          MissingDefinitionClassContextImpl.builder()
              .setClassContext(context.asClass().getClassReference());
    } else if (context.isField()) {
      builder =
          MissingDefinitionFieldContextImpl.builder()
              .setFieldContext(context.asField().getFieldReference());
    } else if (context.isMethod()) {
      builder =
          MissingDefinitionMethodContextImpl.builder()
              .setMethodContext(context.asMethod().getMethodReference());
    } else {
      throw new Unreachable();
    }
    return builder.setOrigin(context.getOrigin()).build();
  }

  public static String toSourceString(MissingDefinitionContext missingDefinitionContext) {
    return MissingDefinitionContextUtils.apply(
        missingDefinitionContext,
        classContext -> classContext.getClassReference().getTypeName(),
        fieldContext -> FieldReferenceUtils.toSourceString(fieldContext.getFieldReference()),
        methodContext -> MethodReferenceUtils.toSourceString(methodContext.getMethodReference()));
  }
}
