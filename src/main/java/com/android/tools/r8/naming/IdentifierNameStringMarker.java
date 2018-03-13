// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.naming.IdentifierNameStringUtils.identifyIdentiferNameString;
import static com.android.tools.r8.naming.IdentifierNameStringUtils.inferMemberOrTypeFromNameString;
import static com.android.tools.r8.naming.IdentifierNameStringUtils.isReflectionMethod;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexItemBasedString;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.google.common.collect.Streams;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class IdentifierNameStringMarker {
  private final AppInfo appInfo;
  private final DexItemFactory dexItemFactory;
  private final Set<DexItem> identifierNameStrings;

  public IdentifierNameStringMarker(AppInfoWithLiveness appInfo) {
    this.appInfo = appInfo;
    this.dexItemFactory = appInfo.dexItemFactory;
    // Note that this info is only available at AppInfoWithLiveness.
    this.identifierNameStrings = appInfo.identifierNameStrings;
  }

  public void decoupleIdentifierNameStringsInFields() {
    for (DexProgramClass clazz : appInfo.classes()) {
      clazz.forEachField(this::decoupleIdentifierNameStringInField);
    }
  }

  private void decoupleIdentifierNameStringInField(DexEncodedField encodedField) {
    if (!identifierNameStrings.contains(encodedField.field)) {
      return;
    }
    if (!(encodedField.staticValue instanceof DexValueString)) {
      return;
    }
    DexString original = ((DexValueString) encodedField.staticValue).getValue();
    DexItemBasedString itemBasedString = inferMemberOrTypeFromNameString(appInfo, original);
    if (itemBasedString != null) {
      encodedField.staticValue = new DexValueString(itemBasedString);
    }
  }

  public void decoupleIdentifierNameStringsInMethod(DexEncodedMethod encodedMethod, IRCode code) {
    for (BasicBlock block : code.blocks) {
      InstructionListIterator iterator = block.listIterator();
      while (iterator.hasNext()) {
        Instruction instruction = iterator.next();
        if (instruction.isStaticPut()) {
          StaticPut staticPut = instruction.asStaticPut();
          DexField field = staticPut.getField();
          if (identifierNameStrings.contains(field)) {
            Value in = staticPut.inValue();
            Value newIn = decoupleIdentifierIfNecessary(code, iterator, staticPut, in);
            if (newIn != in) {
              iterator.replaceCurrentInstruction(
                  new StaticPut(staticPut.getType(), newIn, field));
              encodedMethod.markUseIdentifierNameString();
            }
          }
        } else if (instruction.isInstancePut()) {
          InstancePut instancePut = instruction.asInstancePut();
          DexField field = instancePut.getField();
          if (identifierNameStrings.contains(field)) {
            Value in = instancePut.value();
            Value newIn = decoupleIdentifierIfNecessary(code, iterator, instancePut, in);
            if (newIn != in) {
              iterator.replaceCurrentInstruction(
                  new InstancePut(instancePut.getType(), field, instancePut.object(), newIn));
              encodedMethod.markUseIdentifierNameString();
            }
          }
        } else if (instruction.isInvokeMethod()) {
          InvokeMethod invoke = instruction.asInvokeMethod();
          DexMethod invokedMethod = invoke.getInvokedMethod();
          if (identifierNameStrings.contains(invokedMethod)) {
            List<Value> ins = invoke.arguments();
            Value[] changes = new Value [ins.size()];
            if (isReflectionMethod(dexItemFactory, invokedMethod)) {
              decoupleReflectiveMemberIdentifier(code, iterator, invoke, changes);
            } else {
              for (int i = 0; i < ins.size(); i++) {
                Value in = ins.get(i);
                Value newIn = decoupleIdentifierIfNecessary(code, iterator, invoke, in);
                if (newIn != in) {
                  changes[i] = newIn;
                }
              }
            }
            if (!Arrays.stream(changes).allMatch(Objects::isNull)) {
              List<Value> newIns =
                  Streams.mapWithIndex(
                      ins.stream(),
                      (in, index) -> changes[(int) index] != null ? changes[(int) index] : in)
                  .collect(Collectors.toList());
              iterator.replaceCurrentInstruction(
                  Invoke.create(
                      invoke.getType(),
                      invokedMethod,
                      invokedMethod.proto,
                      invoke.outValue(),
                      newIns));
              encodedMethod.markUseIdentifierNameString();
            }
          }
        }
      }
    }
  }

  private Value decoupleIdentifierIfNecessary(
      IRCode code, InstructionListIterator iterator, Instruction base, Value in) {
    if (!in.isConstString()) {
      return in;
    }
    DexString original = in.getConstInstruction().asConstString().getValue();
    DexItemBasedString itemBasedString = inferMemberOrTypeFromNameString(appInfo, original);
    if (itemBasedString == null) {
      return in;
    }
    return insertItemBasedString(code, iterator, base, in, itemBasedString);
  }

  private void decoupleReflectiveMemberIdentifier(
      IRCode code, InstructionListIterator iterator, InvokeMethod invoke, Value[] changes) {
    DexItemBasedString itemBasedString = identifyIdentiferNameString(appInfo, invoke);
    if (itemBasedString == null) {
      return;
    }
    boolean isClassForName = invoke.getReturnType().descriptor == dexItemFactory.classDescriptor;
    boolean isReferenceFieldUpdater =
        invoke.getReturnType().descriptor == dexItemFactory.referenceFieldUpdaterDescriptor;
    int positionOfIdentifier =
        isClassForName ? 0 : (isReferenceFieldUpdater ? 2 : 1);
    Value in = invoke.arguments().get(positionOfIdentifier);
    Value newIn = insertItemBasedString(code, iterator, invoke, in, itemBasedString);
    if (newIn != in) {
      changes[positionOfIdentifier] = newIn;
    }
  }

  private Value insertItemBasedString(
      IRCode code,
      InstructionListIterator iterator,
      Instruction base,
      Value in,
      DexItemBasedString itemBasedString) {
    // v_n <- "x.y.z" // in.definition
    // ...
    // ... <- ... v_n ..
    // ...
    // this.fld <- v_n // base
    //
    //   ~>
    //
    // ...
    // v_n' <- DexItemBasedString("Lx/y/z;") // decoupled
    // this.fld <- v_n' // base
    //
    // 1) Move the cursor back to $base
    iterator.previous();
    // 2) Add $decoupled just before $base
    Value newIn = code.createValue(in.outType(), in.getLocalInfo());
    ConstString decoupled = new ConstString(newIn, itemBasedString);
    decoupled.setPosition(base.getPosition());
    iterator.add(decoupled);
    // 3) Restore the cursor
    iterator.next();
    return newIn;
  }
}
