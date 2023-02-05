// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.rewriting;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import java.util.Set;

public class ArtProfileRewritingCfClassSynthesizerDesugaringEventConsumer
    extends CfClassSynthesizerDesugaringEventConsumer {

  private final ConcreteArtProfileCollectionAdditions additionsCollection;
  private final CfClassSynthesizerDesugaringEventConsumer parent;

  private ArtProfileRewritingCfClassSynthesizerDesugaringEventConsumer(
      ConcreteArtProfileCollectionAdditions additionsCollection,
      CfClassSynthesizerDesugaringEventConsumer parent) {
    this.additionsCollection = additionsCollection;
    this.parent = parent;
  }

  public static CfClassSynthesizerDesugaringEventConsumer attach(
      ArtProfileCollectionAdditions artProfileCollectionAdditions,
      CfClassSynthesizerDesugaringEventConsumer eventConsumer) {
    if (artProfileCollectionAdditions.isNop()) {
      return eventConsumer;
    }
    return new ArtProfileRewritingCfClassSynthesizerDesugaringEventConsumer(
        artProfileCollectionAdditions.asConcrete(), eventConsumer);
  }

  @Override
  public void acceptCollectionConversion(ProgramMethod arrayConversion) {
    parent.acceptCollectionConversion(arrayConversion);
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
  public void acceptVarHandleDesugaringClass(DexProgramClass varHandleClass) {
    parent.acceptVarHandleDesugaringClass(varHandleClass);
  }

  @Override
  public Set<DexProgramClass> getSynthesizedClasses() {
    return parent.getSynthesizedClasses();
  }
}
