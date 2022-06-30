// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ir.code.Opcodes.CONST_CLASS;
import static com.android.tools.r8.ir.code.Opcodes.CONST_NUMBER;
import static com.android.tools.r8.ir.code.Opcodes.CONST_STRING;
import static com.android.tools.r8.ir.code.Opcodes.DEX_ITEM_BASED_CONST_STRING;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_GET;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_GET;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.FieldResolutionResult.SingleFieldResolutionResult;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleFieldValue;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.FieldGet;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import it.unimi.dsi.fastutil.Hash.Strategy;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectSortedMap.FastSortedEntrySet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Canonicalize constants.
 */
public class ConstantCanonicalizer {

  // Threshold to limit the number of constant canonicalization.
  private static final int MAX_CANONICALIZED_CONSTANT = 22;

  private final AppView<?> appView;
  private final CodeRewriter codeRewriter;
  private final ProgramMethod context;
  private final IRCode code;
  private final boolean isAccessingVolatileField;

  public ConstantCanonicalizer(
      AppView<?> appView, CodeRewriter codeRewriter, ProgramMethod context, IRCode code) {
    this.appView = appView;
    this.codeRewriter = codeRewriter;
    this.context = context;
    this.code = code;
    this.isAccessingVolatileField = computeIsAccessingVolatileField(appView, code);
  }

  private static boolean computeIsAccessingVolatileField(AppView<?> appView, IRCode code) {
    if (!appView.hasClassHierarchy()) {
      // Conservatively return true.
      return true;
    }
    AppInfoWithClassHierarchy appInfo = appView.appInfoWithClassHierarchy();
    for (FieldInstruction fieldGet :
        code.<FieldInstruction>instructions(Instruction::isFieldInstruction)) {
      SingleFieldResolutionResult<?> resolutionResult =
          appInfo.resolveField(fieldGet.getField()).asSingleFieldResolutionResult();
      if (resolutionResult == null
          || resolutionResult.getResolvedField().getAccessFlags().isVolatile()) {
        return true;
      }
    }
    return false;
  }

  public void canonicalize() {
    Object2ObjectLinkedOpenCustomHashMap<Instruction, List<Value>> valuesDefinedByConstant =
        new Object2ObjectLinkedOpenCustomHashMap<>(
            new Strategy<Instruction>() {

              @Override
              public int hashCode(Instruction candidate) {
                assert candidate.instructionTypeCanBeCanonicalized();
                switch (candidate.opcode()) {
                  case CONST_CLASS:
                    return candidate.asConstClass().getValue().hashCode();
                  case CONST_NUMBER:
                    return Long.hashCode(candidate.asConstNumber().getRawValue())
                        + 13 * candidate.outType().hashCode();
                  case CONST_STRING:
                    return candidate.asConstString().getValue().hashCode();
                  case DEX_ITEM_BASED_CONST_STRING:
                    return candidate.asDexItemBasedConstString().getItem().hashCode();
                  case INSTANCE_GET:
                  case STATIC_GET:
                    return candidate.asFieldGet().getField().hashCode();
                  default:
                    throw new Unreachable();
                }
              }

              @Override
              public boolean equals(Instruction a, Instruction b) {
                if (a == b) {
                  return true;
                }
                if (a == null || b == null || a.getClass() != b.getClass()) {
                  return false;
                }
                if (a.isInstanceGet() && a.getFirstOperand() != b.getFirstOperand()) {
                  return false;
                }
                return a.identicalNonValueNonPositionParts(b);
              }
            });

    // Collect usages of constants that can be canonicalized.
    for (Instruction current : code.instructions()) {
      if (isConstantCanonicalizationCandidate(current)) {
        List<Value> oldValuesDefinedByConstant =
            valuesDefinedByConstant.computeIfAbsent(current, k -> new ArrayList<>());
        oldValuesDefinedByConstant.add(current.outValue());
      }
    }

    if (valuesDefinedByConstant.isEmpty()) {
      return;
    }

    // Double-check the entry block does not have catch handlers.
    // Otherwise, we need to split it before moving canonicalized const-string, which may throw.
    assert !code.entryBlock().hasCatchHandlers();
    FastSortedEntrySet<Instruction, List<Value>> entries =
        valuesDefinedByConstant.object2ObjectEntrySet();
    // Sort the most frequently used constant first and exclude constant use only one time, such
    // as the {@code MAX_CANONICALIZED_CONSTANT} will be canonicalized into the entry block.
    Iterator<Object2ObjectMap.Entry<Instruction, List<Value>>> iterator =
        entries.stream()
            .filter(a -> a.getValue().size() > 1)
            .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
            .limit(MAX_CANONICALIZED_CONSTANT)
            .iterator();

    if (!iterator.hasNext()) {
      return;
    }

    boolean shouldSimplifyIfs = false;
    do {
      Object2ObjectMap.Entry<Instruction, List<Value>> entry = iterator.next();
      Instruction canonicalizedConstant = entry.getKey();
      assert canonicalizedConstant.instructionTypeCanBeCanonicalized();
      Instruction newConst;
      if (canonicalizedConstant.getBlock().isEntry()) {
        newConst = canonicalizedConstant;
      } else {
        switch (canonicalizedConstant.opcode()) {
          case CONST_CLASS:
            newConst = ConstClass.copyOf(code, canonicalizedConstant.asConstClass());
            break;
          case CONST_NUMBER:
            newConst = ConstNumber.copyOf(code, canonicalizedConstant.asConstNumber());
            break;
          case CONST_STRING:
            newConst = ConstString.copyOf(code, canonicalizedConstant.asConstString());
            break;
          case DEX_ITEM_BASED_CONST_STRING:
            newConst =
                DexItemBasedConstString.copyOf(
                    code, canonicalizedConstant.asDexItemBasedConstString());
            break;
          case INSTANCE_GET:
            newConst = InstanceGet.copyOf(code, canonicalizedConstant.asInstanceGet());
            break;
          case STATIC_GET:
            newConst = StaticGet.copyOf(code, canonicalizedConstant.asStaticGet());
            break;
          default:
            throw new Unreachable();
        }
        insertCanonicalizedConstant(newConst);
      }
      for (Value outValue : entry.getValue()) {
        outValue.replaceUsers(newConst.outValue());
      }
      shouldSimplifyIfs |= newConst.outValue().hasUserThatMatches(Instruction::isIf);
    } while (iterator.hasNext());

    shouldSimplifyIfs |= code.removeAllDeadAndTrivialPhis();

    if (shouldSimplifyIfs) {
      codeRewriter.simplifyIf(code);
    }

    assert code.isConsistentSSA(appView);
  }

  public boolean isConstantCanonicalizationCandidate(Instruction instruction) {
    // Interested only in instructions types that can be canonicalized, i.e., ConstClass,
    // ConstNumber, (DexItemBased)?ConstString, InstanceGet and StaticGet.
    switch (instruction.opcode()) {
      case CONST_CLASS:
        // Do not canonicalize ConstClass that may have side effects. Its original instructions
        // will not be removed by dead code remover due to the side effects.
        if (instruction.instructionMayHaveSideEffects(appView, context)) {
          return false;
        }
        break;
      case CONST_NUMBER:
        break;
      case CONST_STRING:
      case DEX_ITEM_BASED_CONST_STRING:
        // Do not canonicalize ConstString instructions if there are monitor operations in the code.
        // That could lead to unbalanced locking and could lead to situations where OOM exceptions
        // could leave a synchronized method without unlocking the monitor.
        if (code.metadata().mayHaveMonitorInstruction()) {
          return false;
        }
        break;
      case INSTANCE_GET:
        {
          InstanceGet instanceGet = instruction.asInstanceGet();
          if (!instanceGet.object().isThis()) {
            // TODO(b/236661949): Generalize this to more receivers. For canonicalization we need
            //  the receiver to be non-null (or the instruction can throw) and we also currently
            //  require the receiver to be defined on-entry, since we always hoist constants to the
            //  entry block.
            return false;
          }
          if (!isReadOfFinalFieldOutsideInitializer(instanceGet)) {
            return false;
          }
          break;
        }
      case STATIC_GET:
        {
          // Canonicalize effectively final fields that are guaranteed to be written before they are
          // read. This is only OK if the instruction cannot have side effects.
          StaticGet staticGet = instruction.asStaticGet();
          if (staticGet.instructionMayHaveSideEffects(appView, context)) {
            return false;
          }
          if (!isReadOfFinalFieldOutsideInitializer(staticGet)
              && !isEffectivelyFinalField(staticGet)) {
            return false;
          }
          break;
        }
      default:
        assert !instruction.instructionTypeCanBeCanonicalized() : instruction.toString();
        return false;
    }
    // Constants with local info must not be canonicalized and must be filtered.
    if (instruction.outValue().hasLocalInfo()) {
      return false;
    }
    // Constants that are used by invoke range are not canonicalized to be compliant with the
    // optimization splitRangeInvokeConstant that gives the register allocator more freedom in
    // assigning register to ranged invokes which can greatly reduce the number of register
    // needed (and thereby code size as well). Thus no need to do a transformation that should
    // be removed later by another optimization.
    if (constantUsedByInvokeRange(instruction)) {
      return false;
    }
    return true;
  }

  private boolean isReadOfFinalFieldOutsideInitializer(FieldGet fieldGet) {
    if (isAccessingVolatileField) {
      // A final field may be initialized concurrently. A requirement for this is that the field is
      // volatile. However, the reading or writing of another volatile field also allows for
      // concurrently initializing a non-volatile field. See also redundant field load elimination.
      return false;
    }
    AppView<? extends AppInfoWithClassHierarchy> appViewWithClassHierarchy =
        appView.withClassHierarchy();
    SingleFieldResolutionResult<?> resolutionResult =
        appViewWithClassHierarchy
            .appInfo()
            .resolveField(fieldGet.getField())
            .asSingleFieldResolutionResult();
    if (resolutionResult == null) {
      // Not known to be final.
      return false;
    }
    if (!resolutionResult.isSingleProgramFieldResolutionResult()) {
      // We can't rely on the final flag of non-program fields.
      return false;
    }
    ProgramField resolvedField = resolutionResult.getSingleProgramField();
    FieldAccessFlags accessFlags = resolvedField.getAccessFlags();
    assert !accessFlags.isVolatile();
    // TODO(b/236661949): Add support for effectively final fields so that this also works well
    //  without -allowaccessmodification.
    if (!accessFlags.isFinal()) {
      return false;
    }
    if (appView.getKeepInfo(resolvedField).isPinned(appView.options())) {
      // The final flag could be unset using reflection.
      return false;
    }
    if (context.getDefinition().isInitializer()
        && context.getAccessFlags().isStatic() == fieldGet.isStaticGet()) {
      if (context.getHolder() == resolvedField.getHolder()) {
        // If this is an initializer on the field's holder, then bail out, since the field value is
        // only known to be final after object/class creation.
        return false;
      }
      if (fieldGet.isInstanceGet()
          && appViewWithClassHierarchy
              .appInfo()
              .isSubtype(context.getHolder(), resolvedField.getHolder())) {
        // If an instance initializer reads a final instance field declared in a super class, we
        // cannot hoist the read above the parent constructor call.
        return false;
      }
    }
    if (!resolutionResult.getInitialResolutionHolder().isResolvable(appView)) {
      // If this field read is guarded by an API level check, hoisting of this field could lead to
      // a ClassNotFoundException on some API levels.
      return false;
    }
    return true;
  }

  private boolean isEffectivelyFinalField(StaticGet staticGet) {
    AbstractValue abstractValue = staticGet.outValue().getAbstractValue(appView, context);
    if (!abstractValue.isSingleFieldValue()) {
      return false;
    }
    SingleFieldValue singleFieldValue = abstractValue.asSingleFieldValue();
    DexType fieldHolderType = singleFieldValue.getField().getHolderType();
    if (context.getDefinition().isClassInitializer()
        && context.getHolderType() == fieldHolderType) {
      // Avoid that canonicalization inserts a read before the unique write in the class
      // initializer, as that would change the program behavior.
      return false;
    }
    DexClass fieldHolder = appView.definitionFor(fieldHolderType);
    return singleFieldValue.getField().lookupOnClass(fieldHolder) != null;
  }

  private void insertCanonicalizedConstant(Instruction canonicalizedConstant) {
    BasicBlock entryBlock = code.entryBlock();
    // Insert the constant instruction at the start of the block right after the argument
    // instructions. It is important that the const instruction is put before any instruction
    // that can throw exceptions (since the value could be used on the exceptional edge).
    InstructionListIterator it = entryBlock.listIterator(code);
    while (it.hasNext()) {
      Instruction next = it.next();
      if (!next.isArgument()) {
        canonicalizedConstant.setPosition(code.getEntryPosition());
        it.previous();
        break;
      }
    }
    it.add(canonicalizedConstant);
  }

  private static boolean constantUsedByInvokeRange(Instruction constant) {
    for (Instruction user : constant.outValue().uniqueUsers()) {
      if (user.isInvoke() && user.asInvoke().requiredArgumentRegisters() > 5) {
        return true;
      }
    }
    return false;
  }
}
