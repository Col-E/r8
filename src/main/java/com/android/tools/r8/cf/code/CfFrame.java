// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import static org.objectweb.asm.Opcodes.F_NEW;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class CfFrame extends CfInstruction {

  private final Int2ReferenceSortedMap<DexType> locals;

  public CfFrame(Int2ReferenceSortedMap<DexType> locals) {
    this.locals = locals;
  }

  @Override
  public void write(MethodVisitor visitor) {
    int type = F_NEW;
    int localsSize = locals.size();
    Object[] localsCopy = new Object[localsSize];
    int localIndex = 0;
    for (Entry<DexType> entry : locals.int2ReferenceEntrySet()) {
      Object typeOpcode = getType(entry.getValue());
      if (typeOpcode == Opcodes.LONG || typeOpcode == Opcodes.DOUBLE) {
        localsCopy[localIndex++] = Opcodes.TOP;
      }
      localsCopy[localIndex++] = typeOpcode;
    }
    // TODO(zerny): Compute the stack types too.
    visitor.visitFrame(type, localsSize, localsCopy, 0, new Object[0]);
  }

  private Object getType(DexType type) {
    if (type == DexItemFactory.nullValueType) {
      return Opcodes.NULL;
    }
    switch (type.toShorty()) {
      case 'L':
        return Type.getType(type.toDescriptorString()).getInternalName();
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
}
