// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.rewriting;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.profile.art.ArtProfileOptions;

public class ProfileRewritingVarHandleDesugaringEventConsumerUtils {

  static void handleVarHandleDesugaringClassContext(
      DexProgramClass varHandleClass,
      ProgramDefinition context,
      ConcreteProfileCollectionAdditions additionsCollection,
      ArtProfileOptions options) {
    if (options.isIncludingVarHandleClasses()) {
      additionsCollection.applyIfContextIsInProfile(
          context,
          additions -> {
            additions.addClassRule(varHandleClass);
            varHandleClass.forEachProgramMethod(
                method -> additions.addMethodRule(method, emptyConsumer()));
          },
          additionsBuilder -> {
            additionsBuilder.addRule(varHandleClass);
            varHandleClass.forEachProgramMethod(additionsBuilder::addRule);
          });
    }
  }
}
