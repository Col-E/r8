// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.TextInputStream;
import com.android.tools.r8.TextOutputStream;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.profile.art.ArtProfileBuilderUtils.MutableArtProfileClassRule;
import com.android.tools.r8.profile.art.ArtProfileBuilderUtils.MutableArtProfileMethodRule;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
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
    for (ArtProfileForRewriting artProfileForRewriting :
        appView.options().getArtProfileOptions().getArtProfilesForRewriting()) {
      ArtProfileProvider artProfileProvider = artProfileForRewriting.getArtProfileProvider();
      ArtProfileConsumer artProfileConsumer =
          EmptyArtProfileConsumer.orEmpty(artProfileForRewriting.getResidualArtProfileConsumer());
      supplyArtProfileConsumer(appView, artProfileConsumer, artProfileProvider);
      artProfileConsumer.finished(appView.reporter());
    }
  }

  private void supplyArtProfileConsumer(
      AppView<?> appView,
      ArtProfileConsumer artProfileConsumer,
      ArtProfileProvider artProfileProvider) {
    ArtProfileConsumerSupplier artProfileConsumerSupplier =
        new ArtProfileConsumerSupplier(artProfileConsumer);
    try {
      ArtProfileRuleConsumer ruleConsumer =
          EmptyArtProfileRuleConsumer.orEmpty(artProfileConsumer.getRuleConsumer());
      artProfileProvider.getArtProfile(
          new ArtProfileBuilder() {

            @Override
            public ArtProfileBuilder addClassRule(
                Consumer<ArtProfileClassRuleBuilder> classRuleBuilderConsumer) {
              MutableArtProfileClassRule classRule = new MutableArtProfileClassRule();
              classRuleBuilderConsumer.accept(classRule);
              ruleConsumer.acceptClassRule(
                  classRule.getClassReference(), classRule.getClassRuleInfo());
              artProfileConsumerSupplier.supply(classRule);
              return this;
            }

            @Override
            public ArtProfileBuilder addMethodRule(
                Consumer<ArtProfileMethodRuleBuilder> methodRuleBuilderConsumer) {
              MutableArtProfileMethodRule methodRule = new MutableArtProfileMethodRule();
              methodRuleBuilderConsumer.accept(methodRule);
              ruleConsumer.acceptMethodRule(
                  methodRule.getMethodReference(), methodRule.getMethodRuleInfo());
              artProfileConsumerSupplier.supply(methodRule);
              return this;
            }

            @Override
            public ArtProfileBuilder addHumanReadableArtProfile(
                TextInputStream textInputStream,
                Consumer<HumanReadableArtProfileParserBuilder> parserBuilderConsumer) {
              HumanReadableArtProfileParser.Builder parserBuilder =
                  HumanReadableArtProfileParser.builder()
                      .setReporter(appView.reporter())
                      .setProfileBuilder(this);
              parserBuilderConsumer.accept(parserBuilder);
              HumanReadableArtProfileParser parser = parserBuilder.build();
              parser.parse(textInputStream, artProfileProvider.getOrigin());
              return this;
            }
          });
    } finally {
      artProfileConsumerSupplier.close();
    }
  }

  @Override
  public ArtProfileCollection withoutPrunedItems(PrunedItems prunedItems) {
    return this;
  }

  private static class ArtProfileConsumerSupplier {

    private final OutputStreamWriter outputStreamWriter;

    ArtProfileConsumerSupplier(ArtProfileConsumer artProfileConsumer) {
      TextOutputStream textOutputStream = artProfileConsumer.getHumanReadableArtProfileConsumer();
      this.outputStreamWriter =
          textOutputStream != null
              ? new OutputStreamWriter(
                  textOutputStream.getOutputStream(), textOutputStream.getCharset())
              : null;
      ;
    }

    void supply(MutableArtProfileClassRule classRule) {
      if (outputStreamWriter != null) {
        try {
          classRule.writeHumanReadableRuleString(outputStreamWriter);
          outputStreamWriter.write('\n');
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }

    void supply(MutableArtProfileMethodRule methodRule) {
      if (outputStreamWriter != null) {
        try {
          methodRule.writeHumanReadableRuleString(outputStreamWriter);
          outputStreamWriter.write('\n');
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }

    void close() {
      if (outputStreamWriter != null) {
        try {
          outputStreamWriter.close();
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
  }
}
