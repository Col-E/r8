// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnostic.internal;

import com.android.tools.r8.diagnostic.DefinitionClassContext;
import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.DefinitionFieldContext;
import com.android.tools.r8.diagnostic.DefinitionMethodContext;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.Definition;
import com.android.tools.r8.graph.ProgramDerivedContext;
import com.android.tools.r8.utils.FieldReferenceUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import java.util.function.Consumer;
import java.util.function.Function;

public class DefinitionContextUtils {

  public static void accept(
      DefinitionContext definitionContext,
      Consumer<DefinitionClassContext> definitionClassContextConsumer,
      Consumer<DefinitionFieldContext> definitionFieldContextConsumer,
      Consumer<DefinitionMethodContext> definitionMethodContextConsumer) {
    if (definitionContext.isClassContext()) {
      definitionClassContextConsumer.accept(definitionContext.asClassContext());
    } else if (definitionContext.isFieldContext()) {
      definitionFieldContextConsumer.accept(definitionContext.asFieldContext());
    } else {
      assert definitionContext.isMethodContext();
      definitionMethodContextConsumer.accept(definitionContext.asMethodContext());
    }
  }

  public static <T> T apply(
      DefinitionContext definitionContext,
      Function<DefinitionClassContext, T> definitionClassContextFn,
      Function<DefinitionFieldContext, T> definitionFieldContextFn,
      Function<DefinitionMethodContext, T> definitionMethodContextFn) {
    if (definitionContext.isClassContext()) {
      return definitionClassContextFn.apply(definitionContext.asClassContext());
    } else if (definitionContext.isFieldContext()) {
      return definitionFieldContextFn.apply(definitionContext.asFieldContext());
    } else {
      assert definitionContext.isMethodContext();
      return definitionMethodContextFn.apply(definitionContext.asMethodContext());
    }
  }

  public static DefinitionContext create(ProgramDerivedContext programDerivedContext) {
    Definition context = programDerivedContext.getContext();
    DefinitionContextBase.Builder<?> builder;
    if (context.isClass()) {
      builder =
          DefinitionClassContextImpl.builder()
              .setClassContext(context.asClass().getClassReference());
    } else if (context.isField()) {
      builder =
          DefinitionFieldContextImpl.builder()
              .setFieldContext(context.asField().getFieldReference());
    } else if (context.isMethod()) {
      builder =
          DefinitionMethodContextImpl.builder()
              .setMethodContext(context.asMethod().getMethodReference());
    } else {
      throw new Unreachable();
    }
    return builder.setOrigin(context.getOrigin()).build();
  }

  public static String toSourceString(DefinitionContext definitionContext) {
    return DefinitionContextUtils.apply(
        definitionContext,
        classContext -> classContext.getClassReference().getTypeName(),
        fieldContext -> FieldReferenceUtils.toSourceString(fieldContext.getFieldReference()),
        methodContext -> MethodReferenceUtils.toSourceString(methodContext.getMethodReference()));
  }
}
