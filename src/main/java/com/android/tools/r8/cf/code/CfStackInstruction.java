// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.CfState.Slot;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.naming.NamingLens;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class CfStackInstruction extends CfInstruction {

  public enum Opcode {
    Pop(Opcodes.POP),
    Pop2(Opcodes.POP2),
    Dup(Opcodes.DUP),
    DupX1(Opcodes.DUP_X1),
    DupX2(Opcodes.DUP_X2),
    Dup2(Opcodes.DUP2),
    Dup2X1(Opcodes.DUP2_X1),
    Dup2X2(Opcodes.DUP2_X2),
    Swap(Opcodes.SWAP);

    private final int opcode;

    Opcode(int opcode) {
      this.opcode = opcode;
    }
  }

  private final Opcode opcode;

  public static CfStackInstruction fromAsm(int opcode) {
    switch (opcode) {
      case Opcodes.POP:
        return new CfStackInstruction(Opcode.Pop);
      case Opcodes.POP2:
        return new CfStackInstruction(Opcode.Pop2);
      case Opcodes.DUP:
        return new CfStackInstruction(Opcode.Dup);
      case Opcodes.DUP_X1:
        return new CfStackInstruction(Opcode.DupX1);
      case Opcodes.DUP_X2:
        return new CfStackInstruction(Opcode.DupX2);
      case Opcodes.DUP2:
        return new CfStackInstruction(Opcode.Dup2);
      case Opcodes.DUP2_X1:
        return new CfStackInstruction(Opcode.Dup2X1);
      case Opcodes.DUP2_X2:
        return new CfStackInstruction(Opcode.Dup2X2);
      case Opcodes.SWAP:
        return new CfStackInstruction(Opcode.Swap);
      default:
        throw new Unreachable("Invalid opcode for CfStackInstruction");
    }
  }

  public static CfStackInstruction popType(ValueType type) {
    return new CfStackInstruction(type.isWide() ? Opcode.Pop2 : Opcode.Pop);
  }

  public CfStackInstruction(Opcode opcode) {
    this.opcode = opcode;
  }

  @Override
  public void write(MethodVisitor visitor, NamingLens lens) {
    visitor.visitInsn(opcode.opcode);
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  public Opcode getOpcode() {
    return opcode;
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    switch (opcode) {
      case Pop:
        {
          Slot pop = state.pop();
          assert !pop.type.isWide();
          break;
        }
      case Pop2:
        {
          Slot value = state.pop();
          if (!value.type.isWide()) {
            state.pop();
            throw new Unimplemented("Building IR for Pop2 of narrow value not supported");
          }
          break;
        }
      case Dup:
        {
          Slot dupValue = state.peek();
          assert !dupValue.type.isWide();
          builder.addMove(dupValue.type, state.push(dupValue).register, dupValue.register);
          break;
        }
      case DupX1:
        {
          Slot value1 = state.pop();
          Slot value2 = state.pop();
          assert !value1.type.isWide();
          assert !value2.type.isWide();
          dupX1(builder, state, value1, value2);
          break;
        }
      case DupX2:
        {
          Slot value1 = state.pop();
          Slot value2 = state.pop();
          assert !value1.type.isWide();
          if (value2.type.isWide()) {
            dupX1(builder, state, value1, value2);
            throw new Unimplemented("Building IR for DupX2 of wide value not supported");
          } else {
            Slot value3 = state.pop();
            assert !value3.type.isWide();
            // Input stack: ..., A:value3, B:value2, C:value1
            Slot outValue1 = state.push(value1);
            Slot outValue3 = state.push(value3);
            Slot outValue2 = state.push(value2);
            Slot outValue1Copy = state.push(value1);
            // Output stack: ..., A:outValue1, B:outValue3, C:outValue2, D:outValue1Copy
            // Move D(outValue1Copy) <- C(value1)
            builder.addMove(value1.type, outValue1Copy.register, value1.register);
            // Move C(outValue2) <- B(value2)
            builder.addMove(value2.type, outValue2.register, value2.register);
            // Move B(outValue3) <- A(value3)
            builder.addMove(value3.type, outValue3.register, value3.register);
            // Move A(outValue1) <- D(outValue1Copy)
            builder.addMove(value1.type, outValue1.register, outValue1Copy.register);
          }
          break;
        }
      case Dup2:
        {
          Slot value1 = state.peek();
          if (value1.type.isWide()) {
            builder.addMove(value1.type, state.push(value1).register, value1.register);
          } else {
            Slot value2 = state.peek(1);
            builder.addMove(value2.type, state.push(value2).register, value2.register);
            builder.addMove(value1.type, state.push(value1).register, value1.register);
          }
          break;
        }
      case Dup2X1:
        {
          Slot value1 = state.pop();
          Slot value2 = state.pop();
          assert !value2.type.isWide();
          if (value1.type.isWide()) {
            dupX1(builder, state, value1, value2);
          } else {
            // Input stack: ..., A:value3, B:value2, C:value1
            Slot value3 = state.pop();
            assert !value3.type.isWide();
            Slot outValue2 = state.push(value2);
            Slot outValue1 = state.push(value1);
            Slot outValue3 = state.push(value3);
            Slot outValue2Copy = state.push(value2);
            Slot outValue1Copy = state.push(value1);
            // Output: ..., A:outValue2, B:outValue1, C:outValue3, D:outValue2Copy, E:outValue1Copy
            // Move E(outValue1Copy) <- C(value1)
            builder.addMove(value1.type, outValue1Copy.register, value1.register);
            // Move D(outValue2Copy) <- B(value2)
            builder.addMove(value2.type, outValue2Copy.register, value2.register);
            // Move C(outValue3) <- A(value3)
            builder.addMove(value3.type, outValue3.register, value3.register);
            // Move B(outValue1) <- E(outValue1Copy)
            builder.addMove(value1.type, outValue1.register, outValue1Copy.register);
            // Move A(outValue2) <- D(outValue2Copy)
            builder.addMove(value2.type, outValue2.register, outValue2Copy.register);
            throw new Unimplemented("Building IR for Dup2X1 narrow not supported");
          }
          break;
        }
      case Dup2X2:
        {
          // Input stack:
          Slot value1 = state.pop();
          Slot value2 = state.pop();
          assert !value2.type.isWide();
          if (value1.type.isWide()) {
            // Input stack: ..., value2, value1
            dupX1(builder, state, value1, value2);
            // Output stack: ..., value1, value2, value1
            throw new Unimplemented("Building IR for Dup2X2 wide not supported");
          } else {
            throw new Unimplemented("Building IR for Dup2X2 narrow not supported");
          }
          // break;
        }
      case Swap:
        {
          Slot value1 = state.pop();
          Slot value2 = state.pop();
          assert !value1.type.isWide();
          assert !value2.type.isWide();
          // Input stack: ..., value2, value1
          dupX1(builder, state, value1, value2);
          // Current stack: ..., value1, value2, value1copy
          state.pop();
          // Output stack: ..., value1, value2
          throw new Unimplemented(
              "Building IR for CfStackInstruction " + opcode + " not supported");
          // break;
        }
    }
  }

  private void dupX1(IRBuilder builder, CfState state, Slot inValue1, Slot inValue2) {
    // Input stack: ..., A:inValue2, B:inValue1 (values already popped)
    Slot outValue1 = state.push(inValue1);
    Slot outValue2 = state.push(inValue2);
    Slot outValue1Copy = state.push(inValue1);
    // Output stack: ..., A:outValue1, B:outValue2, C:outValue1Copy
    // Move C(outValue1Copy) <- B(inValue1)
    builder.addMove(inValue1.type, outValue1Copy.register, inValue1.register);
    // Move B(outValue2) <- A(inValue2)
    builder.addMove(inValue2.type, outValue2.register, inValue2.register);
    // Move A(outValue1) <- C(outValue1Copy)
    builder.addMove(outValue1Copy.type, outValue1.register, outValue1Copy.register);
  }

  @Override
  public boolean emitsIR() {
    return false;
  }
}
