// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.ir.optimize.info.OptimizationFeedback.getSimpleFeedback;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.MethodCollection;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.fixup.TreeFixerBase;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfoFixer;
import com.android.tools.r8.ir.optimize.info.MutableFieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MutableMethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback.OptimizationInfoFixer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Takes as input a mapping from old method signatures to new method signatures (with parameters
 * removed), and rewrites all method definitions in the application to their new method signatures.
 */
public class ArgumentPropagatorApplicationFixer extends TreeFixerBase {

  private final AppView<AppInfoWithLiveness> appView;
  private final ArgumentPropagatorGraphLens graphLens;

  public ArgumentPropagatorApplicationFixer(
      AppView<AppInfoWithLiveness> appView, ArgumentPropagatorGraphLens graphLens) {
    super(appView);
    this.appView = appView;
    this.graphLens = graphLens;
  }

  public void fixupApplication(
      Set<DexProgramClass> affectedClasses, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    // If the graph lens is null, argument propagation did not lead to any parameter removals. In
    // this case there is no needed to fixup the program.
    if (graphLens == null) {
      assert affectedClasses.isEmpty();
      return;
    }

    assert !affectedClasses.isEmpty();

    timing.begin("Fixup application");
    ThreadUtils.processItems(affectedClasses, this::fixupClass, executorService);
    timing.end();

    // Fixup optimization info.
    timing.time("Fixup optimization info", () -> fixupOptimizationInfos(executorService));

    timing.begin("Rewrite AppView");
    appView.rewriteWithLens(graphLens, executorService, timing);
    timing.end();
  }

  private void fixupClass(DexProgramClass clazz) {
    fixupFields(clazz);
    fixupMethods(clazz);
  }

  private void fixupFields(DexProgramClass clazz) {
    clazz.setInstanceFields(
        fixupFields(
            clazz.instanceFields(),
            builder -> builder.setGenericSignature(FieldTypeSignature.noSignature())));
    clazz.setStaticFields(
        fixupFields(
            clazz.staticFields(),
            builder -> builder.setGenericSignature(FieldTypeSignature.noSignature())));
  }

  private void fixupMethods(DexProgramClass clazz) {
    MethodCollection methodCollection = clazz.getMethodCollection();
    methodCollection.replaceMethods(
        method -> {
          DexMethod methodReferenceBeforeParameterRemoval = method.getReference();
          DexMethod methodReferenceAfterParameterRemoval =
              graphLens.getNextMethodSignature(methodReferenceBeforeParameterRemoval);
          if (methodReferenceAfterParameterRemoval == methodReferenceBeforeParameterRemoval
              && !graphLens.hasPrototypeChanges(methodReferenceAfterParameterRemoval)) {
            return method;
          }

          DexEncodedMethod replacement =
              method.toTypeSubstitutedMethod(
                  methodReferenceAfterParameterRemoval,
                  builder -> {
                    if (graphLens.hasPrototypeChanges(methodReferenceAfterParameterRemoval)) {
                      RewrittenPrototypeDescription prototypeChanges =
                          graphLens.getPrototypeChanges(methodReferenceAfterParameterRemoval);
                      builder
                          .apply(prototypeChanges.createParameterAnnotationsRemover(method))
                          .setGenericSignature(MethodTypeSignature.noSignature());
                      if (method.isInstance()
                          && prototypeChanges.getArgumentInfoCollection().isArgumentRemoved(0)) {
                        builder
                            .modifyAccessFlags(flags -> flags.demoteFromFinal().promoteToStatic())
                            .unsetIsLibraryMethodOverride();
                      }
                    }
                  });
          method.setObsolete();
          return replacement;
        });
  }

  private void fixupOptimizationInfos(ExecutorService executorService) throws ExecutionException {
    GraphLens codeLens = graphLens.getPrevious();
    PrunedItems prunedItems = PrunedItems.empty(appView.app());
    getSimpleFeedback()
        .fixupOptimizationInfos(
            appView,
            executorService,
            new OptimizationInfoFixer() {
              @Override
              public void fixup(
                  DexEncodedField field, MutableFieldOptimizationInfo optimizationInfo) {
                optimizationInfo.fixupAbstractValue(appView, graphLens, codeLens);
              }

              @Override
              public void fixup(
                  DexEncodedMethod method, MutableMethodOptimizationInfo optimizationInfo) {
                // Fixup the return value in case the method returns a field that had its signature
                // changed.
                optimizationInfo
                    .fixupAbstractReturnValue(appView, graphLens, codeLens)
                    .fixupInstanceInitializerInfo(appView, graphLens, codeLens, prunedItems);

                // Rewrite the optimization info to account for method signature changes.
                if (graphLens.hasPrototypeChanges(method.getReference())) {
                  RewrittenPrototypeDescription prototypeChanges =
                      graphLens.getPrototypeChanges(method.getReference());
                  MethodOptimizationInfoFixer fixer =
                      prototypeChanges.createMethodOptimizationInfoFixer();
                  optimizationInfo.fixup(appView, fixer);
                }
              }
            });
  }

  @Override
  public DexField fixupFieldReference(DexField field) {
    return graphLens.getNextFieldSignature(field);
  }

  @Override
  public DexMethod fixupMethodReference(DexMethod method) {
    throw new Unreachable();
  }

  @Override
  public DexType fixupType(DexType type) {
    throw new Unreachable();
  }

  @Override
  public DexType mapClassType(DexType type) {
    return type;
  }

  @Override
  public void recordFieldChange(DexField from, DexField to) {
    // Intentionally empty.
  }

  @Override
  public void recordMethodChange(DexMethod from, DexMethod to) {
    // Intentionally empty.
  }

  @Override
  public void recordClassChange(DexType from, DexType to) {
    throw new Unreachable();
  }
}
