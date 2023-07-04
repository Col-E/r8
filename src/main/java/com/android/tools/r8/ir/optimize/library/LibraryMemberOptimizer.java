// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory.LibraryMembers;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.CodeOptimization;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class LibraryMemberOptimizer implements CodeOptimization {

  private final AppView<?> appView;

  /** Library fields that are assumed to be final. */
  private final Set<DexField> finalLibraryFields = Sets.newIdentityHashSet();

  /** The library types that are modeled. */
  private final Set<DexType> modeledLibraryTypes = Sets.newIdentityHashSet();

  private final Map<DexType, LibraryMethodModelCollection<?>> libraryMethodModelCollections =
      new IdentityHashMap<>();

  public LibraryMemberOptimizer(AppView<?> appView, Timing timing) {
    this.appView = appView;
    timing.begin("Register optimizers");
    register(new BooleanMethodOptimizer(appView));
    register(new ByteMethodOptimizer(appView));
    register(new ObjectMethodOptimizer(appView));
    register(new ObjectsMethodOptimizer(appView));
    register(new StringBuilderMethodOptimizer(appView));
    register(new StringMethodOptimizer(appView));
    if (appView.enableWholeProgramOptimizations()) {
      // Subtyping is required to prove the enum class is a subtype of java.lang.Enum.
      register(new EnumMethodOptimizer(appView));
    }

    if (LogMethodOptimizer.isEnabled(appView)) {
      register(new LogMethodOptimizer(appView));
    }
    timing.end();

    timing.time("Initialize final fields", this::initializeFinalLibraryFields);

    // TODO(b/224959526): Implement support for lazy computation of optimization info in D8.
    if (appView.enableWholeProgramOptimizations()) {
      timing.begin("Initialize opt info");
      LibraryOptimizationInfoInitializer libraryOptimizationInfoInitializer =
          new LibraryOptimizationInfoInitializer(appView);
      libraryOptimizationInfoInitializer.run();
      modeledLibraryTypes.addAll(libraryOptimizationInfoInitializer.getModeledLibraryTypes());
      timing.end();
    }
  }

  private void initializeFinalLibraryFields() {
    for (LibraryMembers libraryMembers : appView.dexItemFactory().libraryMembersCollection) {
      libraryMembers.forEachFinalField(finalLibraryFields::add);
    }
  }

  /** Returns true if it is safe to assume that the given library field is final. */
  public boolean isFinalLibraryField(DexEncodedField field) {
    return field.isFinal() && finalLibraryFields.contains(field.getReference());
  }

  /**
   * Returns true if {@param type} is a library type that is modeled in the compiler.
   *
   * <p>In order for library modeling to work in D8, we return a definition for invoke instructions
   * that are guaranteed to dispatch to a library method in {@link
   * InvokeMethod#lookupSingleTarget(AppView, ProgramMethod)}. As part of that, we bail-out if the
   * holder of the targeted method is not a library class. However, what is usually on the library
   * path will be on the program path when compiling the framework itself. To ensure that our
   * library modeling works also for the framework compilation, we maintain the set of types that we
   * model, and treat these types as library types in {@link
   * InvokeMethod#lookupSingleTarget(AppView, ProgramMethod)} although they are on the program path.
   */
  public boolean isModeled(DexType type) {
    return modeledLibraryTypes.contains(type);
  }

  private void register(LibraryMethodModelCollection<?> optimizer) {
    DexType modeledType = optimizer.getType();
    LibraryMethodModelCollection<?> existing =
        libraryMethodModelCollections.put(modeledType, optimizer);
    assert existing == null;
    modeledLibraryTypes.add(modeledType);
  }

  @Override
  public void optimize(
      IRCode code,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    BasicBlockIterator blockIterator = code.listIterator();
    Set<BasicBlock> blocksToRemove = Sets.newIdentityHashSet();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      if (blocksToRemove.contains(block)) {
        continue;
      }

      InstructionListIterator instructionIterator = block.listIterator(code);
      Map<LibraryMethodModelCollection<?>, LibraryMethodModelCollection.State> optimizationStates =
          new IdentityHashMap<>();
      while (instructionIterator.hasNext()) {
        Instruction instruction = instructionIterator.next();
        if (!instruction.isInvokeMethod()) {
          continue;
        }

        InvokeMethod invoke = instruction.asInvokeMethod();
        DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, code.context());
        if (singleTarget == null) {
          continue;
        }

        LibraryMethodModelCollection<?> optimizer =
            libraryMethodModelCollections.get(singleTarget.getHolderType());
        if (optimizer == null) {
          continue;
        }

        if (invoke.hasUnusedOutValue()
            && !singleTarget.getDefinition().isInstanceInitializer()
            && !invoke.instructionMayHaveSideEffects(appView, code.context())) {
          instructionIterator.removeOrReplaceByDebugLocalRead();
          continue;
        }

        LibraryMethodModelCollection.State optimizationState =
            optimizationStates.computeIfAbsent(
                optimizer, LibraryMethodModelCollection::createInitialState);
        optimizer.optimize(
            code,
            blockIterator,
            instructionIterator,
            invoke,
            singleTarget,
            affectedValues,
            blocksToRemove,
            optimizationState,
            methodProcessor,
            methodProcessingContext);
      }
    }

    code.removeBlocks(blocksToRemove);

    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
    code.removeRedundantBlocks();
    assert code.isConsistentSSA(appView);
  }
}
