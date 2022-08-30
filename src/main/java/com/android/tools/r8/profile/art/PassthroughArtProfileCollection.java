// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.profile.art.ArtProfileBuilderUtils.MutableArtProfileClassRule;
import com.android.tools.r8.profile.art.ArtProfileBuilderUtils.MutableArtProfileMethodRule;
import java.util.function.Consumer;

public class PassthroughArtProfileCollection extends ArtProfileCollection {

  private static final PassthroughArtProfileCollection INSTANCE =
      new PassthroughArtProfileCollection();

  private PassthroughArtProfileCollection() {}

  static PassthroughArtProfileCollection getInstance() {
    return INSTANCE;
  }

  @Override
  public ArtProfileCollection rewrittenWithLens(GraphLens lens) {
    return this;
  }

  @Override
  public ArtProfileCollection rewrittenWithLens(NamingLens lens, DexItemFactory dexItemFactory) {
    return this;
  }

  @Override
  public void supplyConsumers(AppView<?> appView) {
    for (ArtProfileInput artProfileInput :
        appView.options().getArtProfileOptions().getArtProfileInputs()) {
      ResidualArtProfileConsumer artProfileConsumer = artProfileInput.getArtProfileConsumer();
      if (artProfileConsumer == null) {
        continue;
      }

      ResidualArtProfileRuleConsumer ruleConsumer = artProfileConsumer.getRuleConsumer();
      if (ruleConsumer != null) {
        artProfileInput.getArtProfile(
            new ArtProfileBuilder() {

              @Override
              public ArtProfileBuilder addClassRule(
                  Consumer<ArtProfileClassRuleBuilder> classRuleBuilderConsumer) {
                MutableArtProfileClassRule classRule = new MutableArtProfileClassRule();
                classRuleBuilderConsumer.accept(classRule);
                ruleConsumer.acceptClassRule(
                    classRule.getClassReference(), classRule.getClassRuleInfo());
                return this;
              }

              @Override
              public ArtProfileBuilder addMethodRule(
                  Consumer<ArtProfileMethodRuleBuilder> methodRuleBuilderConsumer) {
                MutableArtProfileMethodRule methodRule = new MutableArtProfileMethodRule();
                methodRuleBuilderConsumer.accept(methodRule);
                ruleConsumer.acceptMethodRule(
                    methodRule.getMethodReference(), methodRule.getMethodRuleInfo());
                return this;
              }
            });
      }

      artProfileConsumer.finished(appView.reporter());
    }
  }

  @Override
  public ArtProfileCollection withoutPrunedItems(PrunedItems prunedItems) {
    return this;
  }
}
