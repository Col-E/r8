// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.rewriting;

import static com.android.tools.r8.profile.rewriting.ProfileRewritingVarHandleDesugaringEventConsumerUtils.handleVarHandleDesugaringClassContext;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.profile.art.ArtProfileOptions;
import java.util.Set;

public class ProfileRewritingCfClassSynthesizerDesugaringEventConsumer
    extends CfClassSynthesizerDesugaringEventConsumer {

  private final ConcreteProfileCollectionAdditions additionsCollection;
  private final ArtProfileOptions options;
  private final CfClassSynthesizerDesugaringEventConsumer parent;

  private ProfileRewritingCfClassSynthesizerDesugaringEventConsumer(
      ConcreteProfileCollectionAdditions additionsCollection,
      ArtProfileOptions options,
      CfClassSynthesizerDesugaringEventConsumer parent) {
    this.additionsCollection = additionsCollection;
    this.options = options;
    this.parent = parent;
  }

  public static CfClassSynthesizerDesugaringEventConsumer attach(
      AppView<?> appView, CfClassSynthesizerDesugaringEventConsumer eventConsumer) {
    return attach(appView, eventConsumer, ProfileCollectionAdditions.create(appView));
  }

  public static CfClassSynthesizerDesugaringEventConsumer attach(
      AppView<?> appView,
      CfClassSynthesizerDesugaringEventConsumer eventConsumer,
      ProfileCollectionAdditions profileCollectionAdditions) {
    if (profileCollectionAdditions.isNop()) {
      return eventConsumer;
    }
    return new ProfileRewritingCfClassSynthesizerDesugaringEventConsumer(
        profileCollectionAdditions.asConcrete(),
        appView.options().getArtProfileOptions(),
        eventConsumer);
  }

  @Override
  public void acceptCollectionConversion(ProgramMethod arrayConversion, ProgramMethod context) {
    additionsCollection.addMethodAndHolderIfContextIsInProfile(arrayConversion, context);
    parent.acceptCollectionConversion(arrayConversion, context);
  }

  @Override
  public void acceptWrapperProgramClass(DexProgramClass clazz) {
    parent.acceptWrapperProgramClass(clazz);
  }

  @Override
  public void acceptEnumConversionProgramClass(DexProgramClass clazz) {
    parent.acceptEnumConversionProgramClass(clazz);
  }

  @Override
  public void acceptDesugaredLibraryRetargeterDispatchProgramClass(DexProgramClass clazz) {
    parent.acceptDesugaredLibraryRetargeterDispatchProgramClass(clazz);
  }

  @Override
  public void acceptProgramEmulatedInterface(DexProgramClass clazz) {
    parent.acceptProgramEmulatedInterface(clazz);
  }

  @Override
  public void acceptRecordClass(DexProgramClass recordClass) {
    parent.acceptRecordClass(recordClass);
  }

  @Override
  public void acceptRecordClassContext(
      DexProgramClass recordTagClass, DexProgramClass recordClass) {
    additionsCollection.applyIfContextIsInProfile(
        recordClass, additions -> additions.addClassRule(recordTagClass));
    ProgramMethod recordTagInstanceInitializer = recordTagClass.getProgramDefaultInitializer();
    if (recordTagInstanceInitializer != null) {
      recordClass.forEachProgramInstanceInitializer(
          recordInstanceInitializer ->
              additionsCollection.applyIfContextIsInProfile(
                  recordInstanceInitializer,
                  additionsBuilder -> additionsBuilder.addRule(recordTagInstanceInitializer)));
    }
    parent.acceptRecordClassContext(recordTagClass, recordClass);
  }

  @Override
  public void acceptVarHandleDesugaringClass(DexProgramClass clazz) {
    parent.acceptVarHandleDesugaringClass(clazz);
  }

  @Override
  public void acceptVarHandleDesugaringClassContext(
      DexProgramClass clazz, ProgramDefinition context) {
    handleVarHandleDesugaringClassContext(clazz, context, additionsCollection, options);
    parent.acceptVarHandleDesugaringClassContext(clazz, context);
  }

  @Override
  public void finished(AppView<? extends AppInfoWithClassHierarchy> appView) {
    additionsCollection.commit(appView);
    parent.finished(appView);
  }

  @Override
  public Set<DexProgramClass> getSynthesizedClasses() {
    return parent.getSynthesizedClasses();
  }
}
