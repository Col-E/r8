// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.CodeOptimization;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class LibraryMethodOptimizer implements CodeOptimization {

  private final AppView<?> appView;

  /** The library types that are modeled. */
  private final Set<DexType> modeledLibraryTypes = Sets.newIdentityHashSet();

  private final Map<DexType, LibraryMethodModelCollection> libraryMethodModelCollections =
      new IdentityHashMap<>();

  public LibraryMethodOptimizer(AppView<?> appView) {
    this.appView = appView;
    register(new BooleanMethodOptimizer(appView));
    register(new ObjectMethodOptimizer(appView));
    register(new ObjectsMethodOptimizer(appView));
    register(new StringMethodOptimizer(appView));

    if (LogMethodOptimizer.isEnabled(appView)) {
      register(new LogMethodOptimizer(appView));
    }

    LibraryOptimizationInfoInitializer libraryOptimizationInfoInitializer =
        new LibraryOptimizationInfoInitializer(appView);
    libraryOptimizationInfoInitializer.run();
    modeledLibraryTypes.addAll(libraryOptimizationInfoInitializer.getModeledLibraryTypes());
  }

  /**
   * Returns true if {@param type} is a library type that is modeled in the compiler.
   *
   * <p>In order for library modeling to work in D8, we return a definition for invoke instructions
   * that are guaranteed to dispatch to a library method in {@link
   * InvokeMethod#lookupSingleTarget(AppView, DexType)}. As part of that, we bail-out if the holder
   * of the targeted method is not a library class. However, what is usually on the library path
   * will be on the program path when compiling the framework itself. To ensure that our library
   * modeling works also for the framework compilation, we maintain the set of types that we model,
   * and treat these types as library types in {@link InvokeMethod#lookupSingleTarget(AppView,
   * DexType)} although they are on the program path.
   */
  public boolean isModeled(DexType type) {
    return modeledLibraryTypes.contains(type);
  }

  private void register(LibraryMethodModelCollection optimizer) {
    DexType modeledType = optimizer.getType();
    LibraryMethodModelCollection existing =
        libraryMethodModelCollections.put(modeledType, optimizer);
    assert existing == null;
    modeledLibraryTypes.add(modeledType);
  }

  @Override
  public void optimize(
      IRCode code,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor) {
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    InstructionListIterator instructionIterator = code.instructionListIterator();
    while (instructionIterator.hasNext()) {
      Instruction instruction = instructionIterator.next();
      if (instruction.isInvokeMethod()) {
        InvokeMethod invoke = instruction.asInvokeMethod();
        DexEncodedMethod singleTarget =
            invoke.lookupSingleTarget(appView, code.method.method.holder);
        if (singleTarget != null) {
          optimizeInvoke(code, instructionIterator, invoke, singleTarget, affectedValues);
        }
      }
    }
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
  }

  private void optimizeInvoke(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexEncodedMethod singleTarget,
      Set<Value> affectedValues) {
    LibraryMethodModelCollection optimizer =
        libraryMethodModelCollections.getOrDefault(
            singleTarget.method.holder, NopLibraryMethodModelCollection.getInstance());
    optimizer.optimize(code, instructionIterator, invoke, singleTarget, affectedValues);
  }
}
