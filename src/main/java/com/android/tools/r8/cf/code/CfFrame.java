// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import static org.objectweb.asm.Opcodes.F_NEW;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfFrame extends CfInstruction {

  private final Int2ReferenceSortedMap<DexType> locals;

  public CfFrame(Int2ReferenceSortedMap<DexType> locals) {
    this.locals = locals;
  }

  private boolean isWide(DexType type) {
    return type.isPrimitiveType() && (type.toShorty() == 'J' || type.toShorty() == 'D');
  }

  @Override
  public void write(MethodVisitor visitor) {
    if (locals.isEmpty()) {
      visitor.visitFrame(F_NEW, 0, null, 0, null);
      return;
    }
    // Compute the size of locals. Absent indexes are denoted by a single-width element (ie, TOP).
    int maxRegister = locals.lastIntKey();
    int localsCount = 0;
    for (int i = 0; i <= maxRegister; i++) {
      localsCount++;
      DexType type = locals.get(i);
      if (type != null && isWide(type)) {
        i++;
      }
    }
    // Construct the locals type descriptions.
    Object[] localsCopy = new Object[localsCount];
    int localIndex = 0;
    for (int i = 0; i <= maxRegister; i++) {
      DexType type = locals.get(i);
      Object typeOpcode = getType(type);
      localsCopy[localIndex++] = typeOpcode;
      if (type != null && isWide(type)) {
        i++;
      }
    }
    // TODO(zerny): Compute the stack types too.
    visitor.visitFrame(F_NEW, localsCount, localsCopy, 0, null);
  }

  private Object getType(DexType type) {
    if (type == null) {
      return Opcodes.TOP;
    }
    if (type == DexItemFactory.nullValueType) {
      return Opcodes.NULL;
    }
    switch (type.toShorty()) {
      case 'L':
        return type.getInternalName();
      case 'I':
        return Opcodes.INTEGER;
      case 'F':
        return Opcodes.FLOAT;
      case 'J':
        return Opcodes.LONG;
      case 'D':
        return Opcodes.DOUBLE;
      default:
        throw new Unreachable("Unexpected value type: " + type);
    }
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }
}
