// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.rewriting;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.RootSetBuilderEventConsumer;

public class ProfileRewritingRootSetBuilderEventConsumer implements RootSetBuilderEventConsumer {

  private final ConcreteProfileCollectionAdditions additionsCollection;
  private final RootSetBuilderEventConsumer parent;

  private ProfileRewritingRootSetBuilderEventConsumer(
      ConcreteProfileCollectionAdditions additionsCollection, RootSetBuilderEventConsumer parent) {
    this.additionsCollection = additionsCollection;
    this.parent = parent;
  }

  public static RootSetBuilderEventConsumer attach(
      ProfileCollectionAdditions additionsCollection, RootSetBuilderEventConsumer eventConsumer) {
    if (additionsCollection.isNop()) {
      return eventConsumer;
    }
    return new ProfileRewritingRootSetBuilderEventConsumer(
        additionsCollection.asConcrete(), eventConsumer);
  }

  @Override
  public void acceptCompanionClassClinit(ProgramMethod method, ProgramMethod companionMethod) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(companionMethod, method);
    parent.acceptCompanionClassClinit(method, companionMethod);
  }

  @Override
  public void acceptDefaultAsCompanionMethod(ProgramMethod method, ProgramMethod companionMethod) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(companionMethod, method);
    parent.acceptDefaultAsCompanionMethod(method, companionMethod);
  }

  @Override
  public void acceptPrivateAsCompanionMethod(ProgramMethod method, ProgramMethod companionMethod) {
    additionsCollection.applyIfContextIsInProfile(
        method,
        additionsBuilder ->
            additionsBuilder
                .addRule(companionMethod)
                .addRule(companionMethod.getHolder())
                .removeMovedMethodRule(method, companionMethod));
    parent.acceptPrivateAsCompanionMethod(method, companionMethod);
  }

  @Override
  public void acceptStaticAsCompanionMethod(ProgramMethod method, ProgramMethod companionMethod) {
    additionsCollection.applyIfContextIsInProfile(
        method,
        additionsBuilder ->
            additionsBuilder
                .addRule(companionMethod)
                .addRule(companionMethod.getHolder())
                .removeMovedMethodRule(method, companionMethod));
    parent.acceptStaticAsCompanionMethod(method, companionMethod);
  }
}
