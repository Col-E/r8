// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_GET;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_PUT;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_GET;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_PUT;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.FieldGet;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InitClass;
import com.android.tools.r8.ir.code.InstanceFieldInstruction;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.CodeRewriter;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;

public class EnqueuerDeferredTracingRewriter {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final CodeRewriter codeRewriter;
  private final DeadCodeRemover deadCodeRemover;

  EnqueuerDeferredTracingRewriter(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
    this.codeRewriter = new CodeRewriter(appView);
    this.deadCodeRemover = new DeadCodeRemover(appView);
  }

  public CodeRewriter getCodeRewriter() {
    return codeRewriter;
  }

  public DeadCodeRemover getDeadCodeRemover() {
    return deadCodeRemover;
  }

  public void rewriteCode(
      IRCode code,
      Map<DexProgramClass, ProgramMethodSet> initializedClassesWithContexts,
      Map<DexField, ProgramField> prunedFields) {
    // TODO(b/205810841): Consider inserting assume instructions to reduce number of null checks.
    // TODO(b/205810841): Consider running constant canonicalizer.
    ProgramMethod context = code.context();

    // Rewrite field instructions that reference a pruned field.
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    BasicBlockIterator blockIterator = code.listIterator();
    boolean hasChanged = false;
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      InstructionListIterator instructionIterator = block.listIterator(code);
      while (instructionIterator.hasNext()) {
        Instruction instruction = instructionIterator.next();
        switch (instruction.opcode()) {
          case INSTANCE_GET:
            hasChanged |=
                rewriteInstanceGet(
                    code,
                    instructionIterator,
                    instruction.asInstanceGet(),
                    affectedValues,
                    prunedFields);
            break;
          case INSTANCE_PUT:
            hasChanged |=
                rewriteInstancePut(instructionIterator, instruction.asInstancePut(), prunedFields);
            break;
          case STATIC_GET:
            hasChanged |=
                rewriteStaticGet(
                    code,
                    instructionIterator,
                    instruction.asStaticGet(),
                    affectedValues,
                    context,
                    initializedClassesWithContexts,
                    prunedFields);
            break;
          case STATIC_PUT:
            hasChanged |=
                rewriteStaticPut(
                    code,
                    instructionIterator,
                    instruction.asStaticPut(),
                    context,
                    initializedClassesWithContexts,
                    prunedFields);
            break;
          default:
            break;
        }
      }
    }
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
    if (hasChanged) {
      code.removeRedundantBlocks();
    }
  }

  private boolean rewriteInstanceGet(
      IRCode code,
      InstructionListIterator instructionIterator,
      InstanceGet instanceGet,
      Set<Value> affectedValues,
      Map<DexField, ProgramField> prunedFields) {
    ProgramField prunedField = prunedFields.get(instanceGet.getField());
    if (prunedField == null) {
      return false;
    }

    insertDefaultValueForFieldGet(
        code, instructionIterator, instanceGet, affectedValues, prunedField);
    removeOrReplaceInstanceFieldInstructionWithNullCheck(instructionIterator, instanceGet);
    return true;
  }

  private boolean rewriteInstancePut(
      InstructionListIterator instructionIterator,
      InstancePut instancePut,
      Map<DexField, ProgramField> prunedFields) {
    ProgramField prunedField = prunedFields.get(instancePut.getField());
    if (prunedField == null) {
      return false;
    }

    removeOrReplaceInstanceFieldInstructionWithNullCheck(instructionIterator, instancePut);
    return true;
  }

  private boolean rewriteStaticGet(
      IRCode code,
      InstructionListIterator instructionIterator,
      StaticGet staticGet,
      Set<Value> affectedValues,
      ProgramMethod context,
      Map<DexProgramClass, ProgramMethodSet> initializedClassesWithContexts,
      Map<DexField, ProgramField> prunedFields) {
    ProgramField prunedField = prunedFields.get(staticGet.getField());
    if (prunedField == null) {
      return false;
    }

    insertDefaultValueForFieldGet(
        code, instructionIterator, staticGet, affectedValues, prunedField);
    removeOrReplaceStaticFieldInstructionByInitClass(
        code, instructionIterator, context, initializedClassesWithContexts, prunedField);
    return true;
  }

  private boolean rewriteStaticPut(
      IRCode code,
      InstructionListIterator instructionIterator,
      StaticPut staticPut,
      ProgramMethod context,
      Map<DexProgramClass, ProgramMethodSet> initializedClassesWithContexts,
      Map<DexField, ProgramField> prunedFields) {
    ProgramField prunedField = prunedFields.get(staticPut.getField());
    if (prunedField == null) {
      return false;
    }

    removeOrReplaceStaticFieldInstructionByInitClass(
        code, instructionIterator, context, initializedClassesWithContexts, prunedField);
    return true;
  }

  private void insertDefaultValueForFieldGet(
      IRCode code,
      InstructionListIterator instructionIterator,
      FieldGet fieldGet,
      Set<Value> affectedValues,
      ProgramField prunedField) {
    if (fieldGet.hasUsedOutValue()) {
      instructionIterator.previous();
      Value replacement =
          prunedField.getType().isReferenceType()
              ? instructionIterator.insertConstNullInstruction(code, appView.options())
              : instructionIterator.insertConstNumberInstruction(
                  code, appView.options(), 0, fieldGet.getOutType());
      fieldGet.outValue().replaceUsers(replacement, affectedValues);
      instructionIterator.next();
    }
  }

  private void removeOrReplaceInstanceFieldInstructionWithNullCheck(
      InstructionListIterator instructionIterator, InstanceFieldInstruction fieldInstruction) {
    if (fieldInstruction.object().isMaybeNull()) {
      instructionIterator.replaceCurrentInstructionWithNullCheck(
          appView, fieldInstruction.object());
    } else {
      instructionIterator.removeOrReplaceByDebugLocalRead();
    }
  }

  private void removeOrReplaceStaticFieldInstructionByInitClass(
      IRCode code,
      InstructionListIterator instructionIterator,
      ProgramMethod context,
      Map<DexProgramClass, ProgramMethodSet> initializedClassesWithContexts,
      ProgramField prunedField) {
    if (prunedField.getHolder().classInitializationMayHaveSideEffectsInContext(appView, context)) {
      instructionIterator.replaceCurrentInstruction(
          InitClass.builder()
              .setFreshOutValue(code, TypeElement.getInt())
              .setType(prunedField.getHolderType())
              .build());
      initializedClassesWithContexts
          .computeIfAbsent(prunedField.getHolder(), ignoreKey(ProgramMethodSet::createConcurrent))
          .add(context);
    } else {
      instructionIterator.removeOrReplaceByDebugLocalRead();
    }
  }
}
