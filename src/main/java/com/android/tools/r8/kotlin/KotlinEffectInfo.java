// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.kotlin.KmVisitorProviders.KmEffectVisitorProvider;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.Reporter;
import java.util.List;
import kotlinx.metadata.KmEffect;
import kotlinx.metadata.KmEffectInvocationKind;
import kotlinx.metadata.KmEffectType;
import kotlinx.metadata.KmEffectVisitor;

public class KotlinEffectInfo implements EnqueuerMetadataTraceable {

  private final KmEffectType type;
  private final KmEffectInvocationKind invocationKind;
  private final List<KotlinEffectExpressionInfo> constructorArguments;
  private final KotlinEffectExpressionInfo conclusion;

  public KotlinEffectInfo(
      KmEffectType type,
      KmEffectInvocationKind invocationKind,
      List<KotlinEffectExpressionInfo> constructorArguments,
      KotlinEffectExpressionInfo conclusion) {
    this.type = type;
    this.invocationKind = invocationKind;
    this.constructorArguments = constructorArguments;
    this.conclusion = conclusion;
  }

  static KotlinEffectInfo create(KmEffect effect, DexItemFactory factory, Reporter reporter) {
    return new KotlinEffectInfo(
        effect.getType(),
        effect.getInvocationKind(),
        KotlinEffectExpressionInfo.create(effect.getConstructorArguments(), factory, reporter),
        KotlinEffectExpressionInfo.create(effect.getConclusion(), factory, reporter));
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    forEachApply(constructorArguments, arg -> arg::trace, definitionSupplier);
    conclusion.trace(definitionSupplier);
  }

  void rewrite(KmEffectVisitorProvider visitorProvider, AppView<?> appView, NamingLens namingLens) {
    KmEffectVisitor kmEffectVisitor = visitorProvider.get(type, invocationKind);
    conclusion.rewrite(kmEffectVisitor::visitConclusionOfConditionalEffect, appView, namingLens);
    for (KotlinEffectExpressionInfo constructorArgument : constructorArguments) {
      constructorArgument.rewrite(kmEffectVisitor::visitConstructorArgument, appView, namingLens);
    }
    kmEffectVisitor.visitEnd();
  }
}
