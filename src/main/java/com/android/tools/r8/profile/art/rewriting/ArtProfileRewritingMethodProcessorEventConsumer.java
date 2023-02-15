// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.rewriting;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.MethodProcessorEventConsumer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class ArtProfileRewritingMethodProcessorEventConsumer extends MethodProcessorEventConsumer {

  private final ConcreteArtProfileCollectionAdditions additionsCollection;
  private final MethodProcessorEventConsumer parent;

  private ArtProfileRewritingMethodProcessorEventConsumer(
      ConcreteArtProfileCollectionAdditions additionsCollection,
      MethodProcessorEventConsumer parent) {
    this.additionsCollection = additionsCollection;
    this.parent = parent;
  }

  public static MethodProcessorEventConsumer attach(
      AppView<?> appView, MethodProcessorEventConsumer eventConsumer) {
    ArtProfileCollectionAdditions additionsCollection =
        ArtProfileCollectionAdditions.create(appView);
    if (additionsCollection.isNop()) {
      return eventConsumer;
    }
    return new ArtProfileRewritingMethodProcessorEventConsumer(
        additionsCollection.asConcrete(), eventConsumer);
  }

  public static MethodProcessorEventConsumer attach(
      ArtProfileCollectionAdditions artProfileCollectionAdditions,
      MethodProcessorEventConsumer eventConsumer) {
    if (artProfileCollectionAdditions.isNop()) {
      return eventConsumer;
    }
    return new ArtProfileRewritingMethodProcessorEventConsumer(
        artProfileCollectionAdditions.asConcrete(), eventConsumer);
  }

  @Override
  public void acceptAssertionErrorCreateMethod(ProgramMethod method, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(method, context);
    parent.acceptAssertionErrorCreateMethod(method, context);
  }

  @Override
  public void acceptEnumUnboxerCheckNotZeroContext(ProgramMethod method, ProgramMethod context) {
    additionsCollection.applyIfContextIsInProfile(
        context, additionsBuilder -> additionsBuilder.addRule(method).addRule(method.getHolder()));
    parent.acceptEnumUnboxerCheckNotZeroContext(method, context);
  }

  @Override
  public void acceptEnumUnboxerLocalUtilityClassMethodContext(
      ProgramMethod method, ProgramMethod context) {
    additionsCollection.applyIfContextIsInProfile(
        context, additionsBuilder -> additionsBuilder.addRule(method).addRule(method.getHolder()));
    parent.acceptEnumUnboxerLocalUtilityClassMethodContext(method, context);
  }

  @Override
  public void acceptEnumUnboxerSharedUtilityClassMethodContext(
      ProgramMethod method, ProgramMethod context) {
    additionsCollection.applyIfContextIsInProfile(
        context,
        additionsBuilder -> {
          additionsBuilder.addRule(method).addRule(method.getHolder());
          method.getHolder().acceptProgramClassInitializer(additionsBuilder::addRule);
        });
    parent.acceptEnumUnboxerSharedUtilityClassMethodContext(method, context);
  }

  @Override
  public void acceptInstanceInitializerOutline(ProgramMethod method, ProgramMethod context) {
    additionsCollection.applyIfContextIsInProfile(
        context, additionsBuilder -> additionsBuilder.addRule(method).addRule(method.getHolder()));
    parent.acceptInstanceInitializerOutline(method, context);
  }

  @Override
  public void acceptServiceLoaderLoadUtilityMethod(ProgramMethod method, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(method, context);
    parent.acceptServiceLoaderLoadUtilityMethod(method, context);
  }

  @Override
  public void acceptUtilityToStringIfNotNullMethod(ProgramMethod method, ProgramMethod context) {
    additionsCollection.applyIfContextIsInProfile(
        context, additionsBuilder -> additionsBuilder.addRule(method).addRule(method.getHolder()));
    parent.acceptUtilityToStringIfNotNullMethod(method, context);
  }

  @Override
  public void acceptUtilityThrowClassCastExceptionIfNotNullMethod(
      ProgramMethod method, ProgramMethod context) {
    additionsCollection.applyIfContextIsInProfile(
        context, additionsBuilder -> additionsBuilder.addRule(method).addRule(method.getHolder()));
    parent.acceptUtilityThrowClassCastExceptionIfNotNullMethod(method, context);
  }

  @Override
  public void acceptUtilityThrowIllegalAccessErrorMethod(
      ProgramMethod method, ProgramMethod context) {
    additionsCollection.applyIfContextIsInProfile(
        context, additionsBuilder -> additionsBuilder.addRule(method).addRule(method.getHolder()));
    parent.acceptUtilityThrowIllegalAccessErrorMethod(method, context);
  }

  @Override
  public void acceptUtilityThrowIncompatibleClassChangeErrorMethod(
      ProgramMethod method, ProgramMethod context) {
    additionsCollection.applyIfContextIsInProfile(
        context, additionsBuilder -> additionsBuilder.addRule(method).addRule(method.getHolder()));
    parent.acceptUtilityThrowIncompatibleClassChangeErrorMethod(method, context);
  }

  @Override
  public void acceptUtilityThrowNoSuchMethodErrorMethod(
      ProgramMethod method, ProgramMethod context) {
    additionsCollection.applyIfContextIsInProfile(
        context, additionsBuilder -> additionsBuilder.addRule(method).addRule(method.getHolder()));
    parent.acceptUtilityThrowNoSuchMethodErrorMethod(method, context);
  }

  @Override
  public void acceptUtilityThrowRuntimeExceptionWithMessageMethod(
      ProgramMethod method, ProgramMethod context) {
    additionsCollection.applyIfContextIsInProfile(
        context, additionsBuilder -> additionsBuilder.addRule(method).addRule(method.getHolder()));
    parent.acceptUtilityThrowRuntimeExceptionWithMessageMethod(method, context);
  }

  @Override
  public void finished(AppView<AppInfoWithLiveness> appView) {
    additionsCollection.commit(appView);
    parent.finished(appView);
  }
}
