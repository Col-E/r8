// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import static org.objectweb.asm.Opcodes.F_NEW;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.NamingLens;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import java.util.List;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfFrame extends CfInstruction {

  public abstract static class Uninitialized {
    abstract Object getAsmLabel();
  }

  public static class UninitializedNew extends Uninitialized {
    private final CfLabel label;

    public UninitializedNew(CfLabel label) {
      this.label = label;
    }

    @Override
    Object getAsmLabel() {
      return label.getLabel();
    }

    public CfLabel getLabel() {
      return label;
    }
  }

  public static class UninitializedThis extends Uninitialized {
    @Override
    Object getAsmLabel() {
      return Opcodes.UNINITIALIZED_THIS;
    }
  }

  private final Int2ReferenceSortedMap<DexType> locals;
  private final Int2ReferenceSortedMap<Uninitialized> allocators;
  private final List<DexType> stack;

  public CfFrame(
      Int2ReferenceSortedMap<DexType> locals,
      Int2ReferenceSortedMap<Uninitialized> allocators,
      List<DexType> stack) {
    this.locals = locals;
    this.allocators = allocators;
    this.stack = stack;
  }

  public Int2ReferenceSortedMap<DexType> getLocals() {
    return locals;
  }

  public Int2ReferenceSortedMap<Uninitialized> getAllocators() {
    return allocators;
  }

  public List<DexType> getStack() {
    return stack;
  }

  @Override
  public void write(MethodVisitor visitor, NamingLens lens) {
    int stackCount = computeStackCount();
    Object[] stackTypes = computeStackTypes(stackCount, lens);
    int localsCount = computeLocalsCount();
    Object[] localsTypes = computeLocalsTypes(localsCount, lens);
    visitor.visitFrame(F_NEW, localsCount, localsTypes, stackCount, stackTypes);
  }

  private boolean isWide(DexType type) {
    return type.isPrimitiveType() && (type.toShorty() == 'J' || type.toShorty() == 'D');
  }

  private int computeStackCount() {
    return stack.size();
  }

  private Object[] computeStackTypes(int stackCount, NamingLens lens) {
    assert stackCount == stack.size();
    if (stackCount == 0) {
      return null;
    }
    Object[] stackTypes = new Object[stackCount];
    for (int i = 0; i < stackCount; i++) {
      stackTypes[i] = getType(stack.get(i), lens);
    }
    return stackTypes;
  }

  private int computeLocalsCount() {
    if (locals.isEmpty()) {
      return 0;
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
    return localsCount;
  }

  private Object[] computeLocalsTypes(int localsCount, NamingLens lens) {
    if (localsCount == 0) {
      return null;
    }
    int maxRegister = locals.lastIntKey();
    Object[] localsTypes = new Object[localsCount];
    int localIndex = 0;
    for (int i = 0; i <= maxRegister; i++) {
      DexType type = locals.get(i);
      Uninitialized allocator = allocators.get(i);
      Object typeOpcode = allocator == null ? getType(type, lens) : allocator.getAsmLabel();
      localsTypes[localIndex++] = typeOpcode;
      if (type != null && isWide(type)) {
        i++;
      }
    }
    return localsTypes;
  }

  private Object getType(DexType type, NamingLens lens) {
    if (type == null) {
      return Opcodes.TOP;
    }
    if (type == DexItemFactory.nullValueType) {
      return Opcodes.NULL;
    }
    switch (type.toShorty()) {
      case 'L':
        return lens.lookupInternalName(type);
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
