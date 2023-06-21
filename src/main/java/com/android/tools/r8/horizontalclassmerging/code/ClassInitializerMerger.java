// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.code;

import static java.lang.Integer.max;

import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ClasspathMethod;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.CfVersionUtils;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/**
 * Responsible for merging the class initializers in each merge group into a single class
 * initializer.
 */
public class ClassInitializerMerger {

  private final ImmutableList<ProgramMethod> classInitializers;

  private ClassInitializerMerger(ImmutableList<ProgramMethod> classInitializers) {
    this.classInitializers = classInitializers;
  }

  public static ClassInitializerMerger create(MergeGroup group) {
    ClassInitializerMerger.Builder builder = new ClassInitializerMerger.Builder();
    group.forEach(
        clazz -> {
          if (clazz.hasClassInitializer()) {
            builder.add(clazz.getProgramClassInitializer());
          }
        });
    return builder.build();
  }

  public boolean isEmpty() {
    return classInitializers.isEmpty();
  }

  public Code getCode(DexMethod syntheticMethodReference) {
    assert !isEmpty();
    if (isTrivialMerge()) {
      assert IterableUtils.allIdentical(
          classInitializers,
          classInitializer -> classInitializer.getDefinition().getCode().isCfCode());
      return new CfCodeBuilder().build(syntheticMethodReference);
    }
    return new IRProvider(classInitializers, syntheticMethodReference);
  }

  public CfVersion getCfVersion() {
    ProgramMethod classInitializer = ListUtils.first(classInitializers);
    if (classInitializers.size() == 1) {
      DexEncodedMethod method = classInitializer.getDefinition();
      return method.hasClassFileVersion() ? method.getClassFileVersion() : null;
    }
    if (classInitializer.getDefinition().getCode().isCfCode()) {
      assert IterableUtils.allIdentical(
          classInitializers, method -> method.getDefinition().getCode().isCfCode());
      return CfVersionUtils.max(classInitializers);
    }
    return null;
  }

  public boolean isTrivialMerge() {
    ProgramMethod firstClassInitializer = ListUtils.first(classInitializers);
    return firstClassInitializer.getDefinition().getCode().isCfCode();
  }

  public ComputedApiLevel getApiReferenceLevel(AppView<?> appView) {
    assert !classInitializers.isEmpty();
    return ListUtils.fold(
        classInitializers,
        appView.computedMinApiLevel(),
        (accApiLevel, method) -> accApiLevel.max(method.getDefinition().getApiLevel()));
  }

  public void setObsolete() {
    classInitializers.forEach(classInitializer -> classInitializer.getDefinition().setObsolete());
  }

  public static class Builder {

    private final ImmutableList.Builder<ProgramMethod> classInitializers = ImmutableList.builder();

    public void add(ProgramMethod classInitializer) {
      assert classInitializer.getDefinition().isClassInitializer();
      assert classInitializer.getDefinition().hasCode();
      classInitializers.add(classInitializer);
    }

    public ClassInitializerMerger build() {
      return new ClassInitializerMerger(classInitializers.build());
    }
  }

  /** Concatenates a collection of class initializers with CF code into a new piece of CF code. */
  private class CfCodeBuilder {

    private int maxStack = 0;
    private int maxLocals = 0;

    public CfCode build(DexMethod syntheticMethodReference) {
      // Building the instructions will adjust maxStack and maxLocals. Build it here before invoking
      // the CfCode constructor to ensure that the value passed in is the updated values.
      Position callerPosition =
          SyntheticPosition.builder().setLine(0).setMethod(syntheticMethodReference).build();
      List<CfInstruction> instructions = buildInstructions(callerPosition);
      return new CfCode(
          syntheticMethodReference.getHolderType(), maxStack, maxLocals, instructions);
    }

    private List<CfInstruction> buildInstructions(Position callerPosition) {
      List<CfInstruction> newInstructions = new ArrayList<>();
      classInitializers.forEach(
          classInitializer -> addCfCode(newInstructions, classInitializer, callerPosition));
      newInstructions.add(CfReturnVoid.INSTANCE);
      return newInstructions;
    }

    private void addCfCode(
        List<CfInstruction> newInstructions, ProgramMethod method, Position callerPosition) {
      CfCode code = method.getDefinition().getCode().asCfCode();
      maxStack = max(maxStack, code.getMaxStack());
      maxLocals = max(maxLocals, code.getMaxLocals());
      CfLabel endLabel = new CfLabel();
      boolean requiresLabel = false;
      int index = 1;
      for (CfInstruction instruction : code.getInstructions()) {
        if (instruction.isPosition()) {
          CfPosition cfPosition = instruction.asPosition();
          newInstructions.add(
              new CfPosition(
                  cfPosition.getLabel(),
                  cfPosition.getPosition().withOutermostCallerPosition(callerPosition)));
        } else if (instruction.isReturn()) {
          if (code.getInstructions().size() != index) {
            newInstructions.add(new CfGoto(endLabel));
            requiresLabel = true;
          }
        } else {
          newInstructions.add(instruction);
        }
        index++;
      }
      if (requiresLabel) {
        newInstructions.add(endLabel);
      }
    }
  }

  /**
   * Provides a piece of {@link IRCode} that is the concatenation of a collection of class
   * initializers.
   */
  static class IRProvider extends Code {

    private final ImmutableList<ProgramMethod> classInitializers;
    private final DexMethod syntheticMethodReference;

    private IRProvider(
        ImmutableList<ProgramMethod> classInitializers, DexMethod syntheticMethodReference) {
      this.classInitializers = classInitializers;
      this.syntheticMethodReference = syntheticMethodReference;
    }

    @Override
    public IRCode buildIR(
        ProgramMethod method,
        AppView<?> appView,
        Origin origin,
        MutableMethodConversionOptions conversionOptions) {
      assert !classInitializers.isEmpty();

      Position callerPosition =
          SyntheticPosition.builder().setLine(0).setMethod(syntheticMethodReference).build();
      IRMetadata metadata = new IRMetadata();
      NumberGenerator blockNumberGenerator = new NumberGenerator();
      NumberGenerator valueNumberGenerator = new NumberGenerator();

      BasicBlock block = new BasicBlock();
      block.setNumber(blockNumberGenerator.next());

      // Add "invoke-static <clinit>" for each of the class initializers to the exit block.
      for (ProgramMethod classInitializer : classInitializers) {
        block.add(
            InvokeStatic.builder()
                .setMethod(classInitializer.getReference())
                .setPosition(callerPosition)
                .build(),
            metadata);
      }

      // Add "return-void" to exit block.
      block.add(Return.builder().setPosition(Position.none()).build(), metadata);
      block.setFilled();

      IRCode code =
          new IRCode(
              appView.options(),
              method,
              callerPosition,
              ListUtils.newLinkedList(block),
              valueNumberGenerator,
              blockNumberGenerator,
              metadata,
              origin,
              conversionOptions);

      ListIterator<BasicBlock> blockIterator = code.listIterator();
      InstructionListIterator instructionIterator = blockIterator.next().listIterator(code);

      Set<BasicBlock> blocksToRemove = Sets.newIdentityHashSet();
      for (ProgramMethod classInitializer : classInitializers) {
        if (!instructionIterator.hasNext()) {
          instructionIterator = blockIterator.next().listIterator(code);
        }

        InvokeStatic invoke = instructionIterator.next().asInvokeStatic();
        assert invoke != null;

        IRCode inliningIR =
            classInitializer
                .getDefinition()
                .getCode()
                .buildInliningIR(
                    method,
                    classInitializer,
                    appView,
                    appView.codeLens(),
                    valueNumberGenerator,
                    callerPosition,
                    classInitializer.getOrigin(),
                    RewrittenPrototypeDescription.none());
        classInitializer.getDefinition().setObsolete();

        DexProgramClass downcast = null;
        instructionIterator.previous();
        instructionIterator.inlineInvoke(
            appView, code, inliningIR, blockIterator, blocksToRemove, downcast);
      }

      // Cleanup.
      code.removeBlocks(blocksToRemove);
      code.removeAllDeadAndTrivialPhis();
      code.removeRedundantBlocks();

      assert code.isConsistentSSA(appView);

      return code;
    }

    @Override
    protected int computeHashCode() {
      throw new Unreachable();
    }

    @Override
    protected boolean computeEquals(Object other) {
      throw new Unreachable();
    }

    @Override
    public int estimatedDexCodeSizeUpperBoundInBytes() {
      throw new Unreachable();
    }

    @Override
    public boolean isEmptyVoidMethod() {
      throw new Unreachable();
    }

    @Nonnull
    @Override
    public Code copySubtype() {
      return this;
    }

    @Override
    public void registerCodeReferences(ProgramMethod method, UseRegistry registry) {
      throw new Unreachable();
    }

    @Override
    public void registerCodeReferencesForDesugaring(ClasspathMethod method, UseRegistry registry) {
      throw new Unreachable();
    }

    @Override
    public String toString() {
      throw new Unreachable();
    }

    @Override
    public String toString(DexEncodedMethod method, RetracerForCodePrinting retracer) {
      throw new Unreachable();
    }
  }
}
