// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.consume;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteIfNotNull;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteList;
import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.shaking.EnqueuerMetadataTraceable;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Consumer;
import kotlinx.metadata.KmConstantValue;
import kotlinx.metadata.KmEffectExpression;

public class KotlinEffectExpressionInfo implements EnqueuerMetadataTraceable {

  private static final List<KotlinEffectExpressionInfo> NO_EXPRESSIONS = ImmutableList.of();
  private static final KotlinEffectExpressionInfo NO_EXPRESSION =
      new KotlinEffectExpressionInfo(0, 0, null, null, NO_EXPRESSIONS, NO_EXPRESSIONS);

  private final int flags;
  private final Integer parameterIndex;
  private final KmConstantValue constantValue;
  private final KotlinTypeInfo isInstanceType;
  private final List<KotlinEffectExpressionInfo> andArguments;
  private final List<KotlinEffectExpressionInfo> orArguments;

  private KotlinEffectExpressionInfo(
      int flags,
      Integer parameterIndex,
      KmConstantValue constantValue,
      KotlinTypeInfo isInstanceType,
      List<KotlinEffectExpressionInfo> andArguments,
      List<KotlinEffectExpressionInfo> orArguments) {
    this.flags = flags;
    this.parameterIndex = parameterIndex;
    this.constantValue = constantValue;
    this.isInstanceType = isInstanceType;
    this.andArguments = andArguments;
    this.orArguments = orArguments;
  }

  static KotlinEffectExpressionInfo create(
      KmEffectExpression effectExpression, DexItemFactory factory, Reporter reporter) {
    if (effectExpression == null) {
      return NO_EXPRESSION;
    }
    return new KotlinEffectExpressionInfo(
        effectExpression.getFlags(),
        effectExpression.getParameterIndex(),
        effectExpression.getConstantValue(),
        KotlinTypeInfo.create(effectExpression.isInstanceType(), factory, reporter),
        create(effectExpression.getAndArguments(), factory, reporter),
        create(effectExpression.getOrArguments(), factory, reporter));
  }

  static List<KotlinEffectExpressionInfo> create(
      List<KmEffectExpression> effectExpressions, DexItemFactory factory, Reporter reporter) {
    if (effectExpressions.isEmpty()) {
      return NO_EXPRESSIONS;
    }
    ImmutableList.Builder<KotlinEffectExpressionInfo> builder = ImmutableList.builder();
    for (KmEffectExpression effectExpression : effectExpressions) {
      builder.add(create(effectExpression, factory, reporter));
    }
    return builder.build();
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    if (this == NO_EXPRESSION) {
      return;
    }
    if (isInstanceType != null) {
      isInstanceType.trace(definitionSupplier);
    }
    forEachApply(andArguments, arg -> arg::trace, definitionSupplier);
    forEachApply(orArguments, arg -> arg::trace, definitionSupplier);
  }

  boolean rewrite(Consumer<KmEffectExpression> consumer, AppView<?> appView) {
    if (this == NO_EXPRESSION) {
      return false;
    }
    KmEffectExpression effectExpression = consume(new KmEffectExpression(), consumer);
    effectExpression.setFlags(flags);
    effectExpression.setParameterIndex(parameterIndex);
    if (constantValue != null) {
      effectExpression.setConstantValue(constantValue);
    }
    boolean rewritten =
        rewriteIfNotNull(
            appView, isInstanceType, effectExpression::setInstanceType, KotlinTypeInfo::rewrite);
    rewritten |=
        rewriteList(
            appView,
            andArguments,
            effectExpression.getAndArguments(),
            KotlinEffectExpressionInfo::rewrite);
    rewritten |=
        rewriteList(
            appView,
            orArguments,
            effectExpression.getOrArguments(),
            KotlinEffectExpressionInfo::rewrite);
    return rewritten;
  }
}
