// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexItemBasedString;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
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
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class IdentifierNameStringMarker {
  private final AppInfoWithLiveness appInfo;
  private final DexItemFactory dexItemFactory;
  private final Set<DexItem> identifierNameStrings;

  public IdentifierNameStringMarker(AppInfoWithLiveness appInfo) {
    this.appInfo = appInfo;
    this.dexItemFactory = appInfo.dexItemFactory;
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
    String maybeDescriptor =
        DescriptorUtils.javaTypeToDescriptorIfValidJavaType(original.toString());
    if (maybeDescriptor == null) {
      return;
    }
    DexType type = dexItemFactory.createType(maybeDescriptor);
    DexItemBasedString typeString = dexItemFactory.createItemBasedString(type);
    encodedField.staticValue = new DexValueString(typeString);
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
            Value newIn = decoupleTypeIdentifierIfNecessary(code, iterator, staticPut, in);
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
            Value newIn = decoupleTypeIdentifierIfNecessary(code, iterator, instancePut, in);
            if (newIn != in) {
              List<Value> values = new ArrayList<>(2);
              values.add(newIn);
              values.add(instancePut.object());
              iterator.replaceCurrentInstruction(
                  new InstancePut(instancePut.getType(), values, field));
              encodedMethod.markUseIdentifierNameString();
            }
          }
        } else if (instruction.isInvokeMethod()) {
          InvokeMethod invoke = instruction.asInvokeMethod();
          DexMethod invokedMethod = invoke.getInvokedMethod();
          if (identifierNameStrings.contains(invokedMethod)) {
            List<Value> ins = invoke.arguments();
            List<Value> newIns;
            if (isReflectiveCase(invokedMethod.proto)) {
              Value in = ins.get(1);
              Value newIn =
                  decoupleReflectiveMemberIdentifier(code, iterator, invoke, in);
              newIns =
                  ins.stream()
                      .map(i -> i == in ? newIn : i)
                      .collect(Collectors.toList());
            } else {
              newIns =
                  ins.stream()
                      .map(in -> decoupleTypeIdentifierIfNecessary(code, iterator, invoke, in))
                      .collect(Collectors.toList());
            }
            if (!ins.equals(newIns)) {
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

  private Value decoupleTypeIdentifierIfNecessary(
      IRCode code, InstructionListIterator iterator, Instruction base, Value in) {
    if (!in.isConstString()) {
      return in;
    }
    ConstString constString = in.getConstInstruction().asConstString();
    String maybeDescriptor =
        DescriptorUtils.javaTypeToDescriptorIfValidJavaType(constString.getValue().toString());
    if (maybeDescriptor == null) {
      return in;
    }
    DexType type = dexItemFactory.createType(maybeDescriptor);
    DexItemBasedString typeString = dexItemFactory.createItemBasedString(type);
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
    ConstString decoupled = new ConstString(newIn, typeString);
    decoupled.setPosition(base.getPosition());
    iterator.add(decoupled);
    // 3) Restore the cursor
    iterator.next();
    return newIn;
  }

  private Value decoupleReflectiveMemberIdentifier(
    IRCode code, InstructionListIterator iterator, InvokeMethod invoke, Value in) {
    // TODO(b/36799092): special reflection cases.
    return in;
  }

  private boolean isReflectiveCase(DexProto proto) {
    // (Class, String) -> java.lang.reflect.Field
    // (Class, String, Class[]) -> java.lang.reflect.Method
    int numOfParams = proto.parameters.size();
    if (numOfParams != 2 && numOfParams != 3) {
      return false;
    }
    if (numOfParams == 2) {
      if (proto.returnType.descriptor != dexItemFactory.fieldDescriptor) {
        return false;
      }
    } else {
      if (proto.returnType.descriptor != dexItemFactory.methodDescriptor) {
        return false;
      }
    }
    if (proto.parameters.values[0].descriptor != dexItemFactory.classDescriptor) {
      return false;
    }
    if (proto.parameters.values[1].descriptor != dexItemFactory.stringDescriptor) {
      return false;
    }
    if (numOfParams == 3) {
      if (proto.parameters.values[2].toDescriptorString().equals("[Ljava/lang/Class;")) {
        return false;
      }
    }
    return true;
  }

}
