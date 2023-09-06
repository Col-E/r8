// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.function.Consumer;
import org.objectweb.asm.Opcodes;

public class FieldAccessorBuilder {

  private DexField field;
  private OptionalBool isInstanceField = OptionalBool.unknown();
  private OptionalBool isSetter = OptionalBool.unknown();
  private DexMethod sourceMethod;

  private FieldAccessorBuilder() {}

  public static FieldAccessorBuilder builder() {
    return new FieldAccessorBuilder();
  }

  public FieldAccessorBuilder apply(Consumer<FieldAccessorBuilder> consumer) {
    consumer.accept(this);
    return this;
  }

  public FieldAccessorBuilder applyIf(
      boolean condition,
      Consumer<FieldAccessorBuilder> thenConsumer,
      Consumer<FieldAccessorBuilder> elseConsumer) {
    return apply(condition ? thenConsumer : elseConsumer);
  }

  public FieldAccessorBuilder setField(DexClassAndField field) {
    return field.getAccessFlags().isStatic()
        ? setStaticField(field.getReference())
        : setInstanceField(field.getReference());
  }

  public FieldAccessorBuilder setGetter() {
    isSetter = OptionalBool.FALSE;
    return this;
  }

  public FieldAccessorBuilder setInstanceField(DexField field) {
    this.field = field;
    this.isInstanceField = OptionalBool.TRUE;
    return this;
  }

  public FieldAccessorBuilder setSetter() {
    isSetter = OptionalBool.TRUE;
    return this;
  }

  public FieldAccessorBuilder setSourceMethod(DexMethod sourceMethod) {
    this.sourceMethod = sourceMethod;
    return this;
  }

  public FieldAccessorBuilder setStaticField(DexField field) {
    this.field = field;
    this.isInstanceField = OptionalBool.FALSE;
    return this;
  }

  @SuppressWarnings("BadImport")
  public CfCode build() {
    assert validate();
    int maxStack = 0;
    int maxLocals = 0;
    Builder<CfInstruction> instructions = ImmutableList.builder();
    if (isInstanceField()) {
      // Load the receiver.
      instructions.add(new CfLoad(ValueType.OBJECT, maxLocals));
      maxStack += 1;
      maxLocals += 1;
    }

    if (isSetter()) {
      // Load the argument.
      ValueType fieldType = ValueType.fromDexType(field.getType());
      instructions.add(new CfLoad(fieldType, maxLocals));
      maxStack += fieldType.requiredRegisters();
      maxLocals += fieldType.requiredRegisters();
    }

    // Get or set the field.
    int opcode =
        Opcodes.GETSTATIC + BooleanUtils.intValue(isSetter()) + (isInstanceField.ordinal() << 1);
    instructions.add(CfFieldInstruction.create(opcode, field, field));

    // Return.
    if (isSetter()) {
      instructions.add(new CfReturnVoid());
    } else {
      ValueType fieldType = ValueType.fromDexType(field.getType());
      maxStack = Math.max(fieldType.requiredRegisters(), maxStack);
      instructions.add(new CfReturn(fieldType));
    }

    ImmutableList<CfTryCatch> tryCatchRanges = ImmutableList.of();
    ImmutableList<CfCode.LocalVariableInfo> localVariables = ImmutableList.of();
    return new CfCode(
        sourceMethod.getHolderType(),
        maxStack,
        maxLocals,
        instructions.build(),
        tryCatchRanges,
        localVariables);
  }

  private boolean isSetter() {
    return isSetter.isTrue();
  }

  private boolean isInstanceField() {
    return isInstanceField.isTrue();
  }

  private boolean validate() {
    assert field != null;
    assert !isInstanceField.isUnknown();
    assert !isSetter.isUnknown();
    assert sourceMethod != null;
    return true;
  }
}
