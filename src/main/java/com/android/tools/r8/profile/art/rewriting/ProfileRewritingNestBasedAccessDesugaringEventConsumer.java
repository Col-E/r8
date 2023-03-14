// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.rewriting;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.nest.NestBasedAccessDesugaringEventConsumer;

public class ProfileRewritingNestBasedAccessDesugaringEventConsumer
    implements NestBasedAccessDesugaringEventConsumer {

  private final ConcreteProfileCollectionAdditions additionsCollection;
  private final NestBasedAccessDesugaringEventConsumer parent;

  private ProfileRewritingNestBasedAccessDesugaringEventConsumer(
      ConcreteProfileCollectionAdditions additionsCollection,
      NestBasedAccessDesugaringEventConsumer parent) {
    this.additionsCollection = additionsCollection;
    this.parent = parent;
  }

  public static NestBasedAccessDesugaringEventConsumer attach(
      ProfileCollectionAdditions additionsCollection,
      NestBasedAccessDesugaringEventConsumer eventConsumer) {
    if (additionsCollection.isNop()) {
      return eventConsumer;
    }
    return new ProfileRewritingNestBasedAccessDesugaringEventConsumer(
        additionsCollection.asConcrete(), eventConsumer);
  }

  @Override
  public void acceptNestConstructorBridge(
      ProgramMethod target,
      ProgramMethod bridge,
      DexProgramClass argumentClass,
      DexClassAndMethod context) {
    if (context.isProgramMethod()) {
      additionsCollection.applyIfContextIsInProfile(
          context.asProgramMethod(),
          additionsBuilder -> additionsBuilder.addRule(argumentClass).addRule(bridge));
    } else {
      additionsCollection.accept(
          additions ->
              additions.addClassRule(argumentClass).addMethodRule(bridge, emptyConsumer()));
    }
    parent.acceptNestConstructorBridge(target, bridge, argumentClass, context);
  }

  @Override
  public void acceptNestFieldGetBridge(
      ProgramField target, ProgramMethod bridge, DexClassAndMethod context) {
    additionsCollection.addMethodIfContextIsInProfile(bridge, context, emptyConsumer());
    parent.acceptNestFieldGetBridge(target, bridge, context);
  }

  @Override
  public void acceptNestFieldPutBridge(
      ProgramField target, ProgramMethod bridge, DexClassAndMethod context) {
    additionsCollection.addMethodIfContextIsInProfile(bridge, context, emptyConsumer());
    parent.acceptNestFieldPutBridge(target, bridge, context);
  }

  @Override
  public void acceptNestMethodBridge(
      ProgramMethod target, ProgramMethod bridge, DexClassAndMethod context) {
    additionsCollection.addMethodIfContextIsInProfile(bridge, context, emptyConsumer());
    parent.acceptNestMethodBridge(target, bridge, context);
  }
}
