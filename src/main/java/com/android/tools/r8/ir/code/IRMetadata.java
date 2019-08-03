// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

public class IRMetadata {

  private long first;
  private long second;

  public IRMetadata() {}

  private IRMetadata(long first, long second) {
    this.first = first;
    this.second = second;
  }

  public static IRMetadata unknown() {
    return new IRMetadata(0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL);
  }

  private boolean get(int bit) {
    if (bit < 64) {
      return isAnySetInFirst(1L << bit);
    } else {
      assert bit < 128;
      int adjusted = bit - 64;
      return isAnySetInSecond(1L << adjusted);
    }
  }

  private boolean isAnySetInFirst(long mask) {
    return (first & mask) != 0;
  }

  private boolean isAnySetInSecond(long mask) {
    return (second & mask) != 0;
  }

  private void set(int bit) {
    if (bit < 64) {
      first |= (1L << bit);
    } else {
      assert bit < 128;
      int adjusted = bit - 64;
      second |= (1L << adjusted);
    }
  }

  public void record(Instruction instruction) {
    set(instruction.opcode());
  }

  public void merge(IRMetadata metadata) {
    first |= metadata.first;
    second |= metadata.second;
  }

  public boolean mayHaveCheckCast() {
    return get(Opcodes.CHECK_CAST);
  }

  public boolean mayHaveConstNumber() {
    return get(Opcodes.CONST_NUMBER);
  }

  public boolean mayHaveConstString() {
    return get(Opcodes.CONST_STRING);
  }

  public boolean mayHaveDebugPosition() {
    return get(Opcodes.DEBUG_POSITION);
  }

  public boolean mayHaveDexItemBasedConstString() {
    return get(Opcodes.DEX_ITEM_BASED_CONST_STRING);
  }

  public boolean mayHaveFieldGet() {
    return mayHaveInstanceGet() || mayHaveStaticGet();
  }

  public boolean mayHaveInstanceGet() {
    return get(Opcodes.INSTANCE_GET);
  }

  public boolean mayHaveInstanceOf() {
    return get(Opcodes.INSTANCE_OF);
  }

  public boolean mayHaveIntSwitch() {
    return get(Opcodes.INT_SWITCH);
  }

  public boolean mayHaveInvokeDirect() {
    return get(Opcodes.INVOKE_DIRECT);
  }

  public boolean mayHaveInvokeInterface() {
    return get(Opcodes.INVOKE_INTERFACE);
  }

  @SuppressWarnings("ConstantConditions")
  public boolean mayHaveInvokeMethod() {
    assert Opcodes.INVOKE_DIRECT <= 64;
    assert Opcodes.INVOKE_INTERFACE <= 64;
    assert Opcodes.INVOKE_POLYMORPHIC <= 64;
    assert Opcodes.INVOKE_STATIC <= 64;
    assert Opcodes.INVOKE_SUPER <= 64;
    assert Opcodes.INVOKE_VIRTUAL <= 64;
    long mask =
        (1L << Opcodes.INVOKE_DIRECT)
            | (1L << Opcodes.INVOKE_INTERFACE)
            | (1L << Opcodes.INVOKE_POLYMORPHIC)
            | (1L << Opcodes.INVOKE_STATIC)
            | (1L << Opcodes.INVOKE_SUPER)
            | (1L << Opcodes.INVOKE_VIRTUAL);
    boolean result = isAnySetInFirst(mask);
    assert result
        == (mayHaveInvokeDirect()
            || mayHaveInvokeInterface()
            || mayHaveInvokePolymorphic()
            || mayHaveInvokeStatic()
            || mayHaveInvokeSuper()
            || mayHaveInvokeVirtual());
    return result;
  }

  @SuppressWarnings("ConstantConditions")
  public boolean mayHaveInvokeMethodWithReceiver() {
    assert Opcodes.INVOKE_DIRECT <= 64;
    assert Opcodes.INVOKE_INTERFACE <= 64;
    assert Opcodes.INVOKE_SUPER <= 64;
    assert Opcodes.INVOKE_VIRTUAL <= 64;
    long mask =
        (1L << Opcodes.INVOKE_DIRECT)
            | (1L << Opcodes.INVOKE_INTERFACE)
            | (1L << Opcodes.INVOKE_SUPER)
            | (1L << Opcodes.INVOKE_VIRTUAL);
    boolean result = isAnySetInFirst(mask);
    assert result
        == (mayHaveInvokeDirect()
            || mayHaveInvokeInterface()
            || mayHaveInvokeSuper()
            || mayHaveInvokeVirtual());
    return result;
  }

  public boolean mayHaveInvokePolymorphic() {
    return get(Opcodes.INVOKE_POLYMORPHIC);
  }

  public boolean mayHaveInvokeStatic() {
    return get(Opcodes.INVOKE_STATIC);
  }

  public boolean mayHaveInvokeSuper() {
    return get(Opcodes.INVOKE_SUPER);
  }

  public boolean mayHaveInvokeVirtual() {
    return get(Opcodes.INVOKE_VIRTUAL);
  }

  public boolean mayHaveMonitorInstruction() {
    return get(Opcodes.MONITOR);
  }

  public boolean mayHaveStaticGet() {
    return get(Opcodes.STATIC_GET);
  }

  public boolean mayHaveStringSwitch() {
    return get(Opcodes.STRING_SWITCH);
  }
}
