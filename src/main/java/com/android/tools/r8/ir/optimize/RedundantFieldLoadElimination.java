// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Eliminate redundant field loads.
 *
 * <p>Simple algorithm that goes through all blocks in one pass in dominator order and propagates
 * active field sets across control-flow edges where the target has only one predecessor.
 */
// TODO(ager): Evaluate speed/size for computing active field sets in a fixed-point computation.
public class RedundantFieldLoadElimination {

  private final AppInfo appInfo;
  private final DexEncodedMethod method;
  private final IRCode code;
  private final boolean enableWholeProgramOptimizations;
  private final DominatorTree dominatorTree;

  // Maps keeping track of fields that have an already loaded value at basic block entry.
  private final HashMap<BasicBlock, HashMap<FieldAndObject, Instruction>>
      activeInstanceFieldsAtEntry = new HashMap<>();
  private final HashMap<BasicBlock, HashMap<DexField, Instruction>> activeStaticFieldsAtEntry =
      new HashMap<>();

  // Maps keeping track of fields with already loaded values for the current block during
  // elimination.
  private HashMap<FieldAndObject, Instruction> activeInstanceFields;
  private HashMap<DexField, Instruction> activeStaticFields;

  public RedundantFieldLoadElimination(
      AppInfo appInfo, IRCode code, boolean enableWholeProgramOptimizations) {
    this.appInfo = appInfo;
    this.method = code.method;
    this.code = code;
    this.enableWholeProgramOptimizations = enableWholeProgramOptimizations;
    dominatorTree = new DominatorTree(code);
  }

  private static class FieldAndObject {
    private final DexField field;
    private final Value object;

    private FieldAndObject(DexField field, Value receiver) {
      this.field = field;
      this.object = receiver;
    }

    @Override
    public int hashCode() {
      return field.hashCode() * 7 + object.hashCode();
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof FieldAndObject)) {
        return false;
      }
      FieldAndObject o = (FieldAndObject) other;
      return o.object == object && o.field == field;
    }
  }

  private boolean couldBeVolatile(DexField field) {
    if (!enableWholeProgramOptimizations && field.getHolder() != method.method.getHolder()) {
      return true;
    }
    DexEncodedField definition = appInfo.definitionFor(field);
    return definition == null || definition.accessFlags.isVolatile();
  }

  public void run() {
    for (BasicBlock block : dominatorTree.getSortedBlocks()) {
      activeInstanceFields =
          activeInstanceFieldsAtEntry.containsKey(block)
              ? activeInstanceFieldsAtEntry.get(block)
              : new HashMap<>();
      activeStaticFields =
          activeStaticFieldsAtEntry.containsKey(block)
              ? activeStaticFieldsAtEntry.get(block)
              : new HashMap<>();
      InstructionListIterator it = block.listIterator();
      while (it.hasNext()) {
        Instruction instruction = it.next();
        if (instruction.isFieldInstruction()) {
          DexField field = instruction.asFieldInstruction().getField();
          if (instruction.isInstancePut() || instruction.isStaticPut()) {
            killActiveFields(instruction.asFieldInstruction());
          } else if (couldBeVolatile(field)) {
            assert instruction.isInstanceGet() || instruction.isStaticGet();
            killAllActiveFields();
          } else {
            assert instruction.isInstanceGet() || instruction.isStaticGet();
            assert !couldBeVolatile(field);
            if (instruction.isInstanceGet() && !instruction.outValue().hasLocalInfo()) {
              Value object = instruction.asInstanceGet().object();
              FieldAndObject fieldAndObject = new FieldAndObject(field, object);
              if (activeInstanceFields.containsKey(fieldAndObject)) {
                Instruction active = activeInstanceFields.get(fieldAndObject);
                eliminateRedundantRead(it, instruction, active);
              } else {
                activeInstanceFields.put(fieldAndObject, instruction);
              }
            } else if (instruction.isStaticGet() && !instruction.outValue().hasLocalInfo()) {
              if (activeStaticFields.containsKey(field)) {
                Instruction active = activeStaticFields.get(field);
                eliminateRedundantRead(it, instruction, active);
              } else {
                // A field get on a different class can cause <clinit> to run and change static
                // field values.
                killActiveFields(instruction.asFieldInstruction());
                activeStaticFields.put(field, instruction);
              }
            }
          }
        }
        if ((instruction.isMonitor() && instruction.asMonitor().isEnter())
            || instruction.isInvokeMethod()) {
          killAllActiveFields();
        }
      }
      propagateActiveFieldsFrom(block);
    }
    assert code.isConsistentSSA();
  }

  private void propagateActiveFieldsFrom(BasicBlock block) {
    for (BasicBlock successor : block.getSuccessors()) {
      // Allow propagation across exceptional edges, just be careful not to propagate if the
      // throwing instruction is a field instruction.
      if (successor.getPredecessors().size() == 1) {
        if (block.hasCatchSuccessor(successor)) {
          Instruction exceptionalExit = block.exceptionalExit();
          if (exceptionalExit != null && exceptionalExit.isFieldInstruction()) {
            killActiveFieldsForExceptionalExit(exceptionalExit.asFieldInstruction());
          }
        }
        assert !activeInstanceFieldsAtEntry.containsKey(successor);
        activeInstanceFieldsAtEntry.put(successor, new HashMap<>(activeInstanceFields));
        assert !activeStaticFieldsAtEntry.containsKey(successor);
        activeStaticFieldsAtEntry.put(successor, new HashMap<>(activeStaticFields));
      }
    }
  }

  private void killAllActiveFields() {
    activeInstanceFields.clear();
    activeStaticFields.clear();
  }

  private void killActiveFields(FieldInstruction instruction) {
    DexField field = instruction.getField();
    if (instruction.isInstancePut()) {
      // Remove all the field/object pairs that refer to this field to make sure
      // that we are conservative.
      List<FieldAndObject> keysToRemove = new ArrayList<>();
      for (FieldAndObject key : activeInstanceFields.keySet()) {
        if (key.field == field) {
          keysToRemove.add(key);
        }
      }
      keysToRemove.forEach((k) -> activeInstanceFields.remove(k));
    } else if (instruction.isInstanceGet()) {
      Value object = instruction.asInstanceGet().object();
      FieldAndObject fieldAndObject = new FieldAndObject(field, object);
      activeInstanceFields.remove(fieldAndObject);
    } else if (instruction.isStaticPut()) {
      if (field.clazz != code.method.method.holder) {
        // Accessing a static field on a different object could cause <clinit> to run which
        // could modify any static field on any other object.
        activeStaticFields.clear();
      } else {
        activeStaticFields.remove(field);
      }
    } else if (instruction.isStaticGet()) {
      if (field.clazz != code.method.method.holder) {
        // Accessing a static field on a different object could cause <clinit> to run which
        // could modify any static field on any other object.
        activeStaticFields.clear();
      }
    }
  }

  // If a field get instruction throws an exception it did not have an effect on the
  // value of the field. Therefore, when propagating across exceptional edges for a
  // field get instruction we have to exclude that field from the set of known
  // field values.
  private void killActiveFieldsForExceptionalExit(FieldInstruction instruction) {
    DexField field = instruction.getField();
    if (instruction.isInstanceGet()) {
      Value object = instruction.asInstanceGet().object();
      FieldAndObject fieldAndObject = new FieldAndObject(field, object);
      activeInstanceFields.remove(fieldAndObject);
    } else if (instruction.isStaticGet()) {
      activeStaticFields.remove(field);
    }
  }

  private void eliminateRedundantRead(
      InstructionListIterator it, Instruction redundant, Instruction active) {
    redundant.outValue().replaceUsers(active.outValue());
    it.removeOrReplaceByDebugLocalRead();
    active.outValue().uniquePhiUsers().forEach(Phi::removeTrivialPhi);
  }
}
