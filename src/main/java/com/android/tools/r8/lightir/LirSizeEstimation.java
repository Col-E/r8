// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.lightir;

import com.android.tools.r8.dex.code.DexAget;
import com.android.tools.r8.dex.code.DexAput;
import com.android.tools.r8.dex.code.DexArrayLength;
import com.android.tools.r8.dex.code.DexBase1Format;
import com.android.tools.r8.dex.code.DexBase2Format;
import com.android.tools.r8.dex.code.DexBase3Format;
import com.android.tools.r8.dex.code.DexCheckCast;
import com.android.tools.r8.dex.code.DexConst16;
import com.android.tools.r8.dex.code.DexConst4;
import com.android.tools.r8.dex.code.DexConstClass;
import com.android.tools.r8.dex.code.DexConstString;
import com.android.tools.r8.dex.code.DexConstWide16;
import com.android.tools.r8.dex.code.DexFillArrayData;
import com.android.tools.r8.dex.code.DexFillArrayDataPayload;
import com.android.tools.r8.dex.code.DexFilledNewArray;
import com.android.tools.r8.dex.code.DexGoto;
import com.android.tools.r8.dex.code.DexInstanceOf;
import com.android.tools.r8.dex.code.DexInvokeCustom;
import com.android.tools.r8.dex.code.DexMonitorEnter;
import com.android.tools.r8.dex.code.DexMonitorExit;
import com.android.tools.r8.dex.code.DexMove;
import com.android.tools.r8.dex.code.DexMoveException;
import com.android.tools.r8.dex.code.DexNewArray;
import com.android.tools.r8.dex.code.DexNewInstance;
import com.android.tools.r8.dex.code.DexNotInt;
import com.android.tools.r8.dex.code.DexNotLong;
import com.android.tools.r8.dex.code.DexPackedSwitch;
import com.android.tools.r8.dex.code.DexPackedSwitchPayload;
import com.android.tools.r8.dex.code.DexSget;
import com.android.tools.r8.dex.code.DexThrow;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.lightir.LirBuilder.IntSwitchPayload;

public class LirSizeEstimation<EV> extends LirParsedInstructionCallback<EV> implements LirOpcodes {

  private int sizeEstimate = 0;

  LirSizeEstimation(LirCode<EV> code) {
    super(code);
  }

  @Override
  public int getCurrentValueIndex() {
    // We don't use value information.
    return 0;
  }

  public int getSizeEstimate() {
    return sizeEstimate;
  }

  /**
   * Most size information can be found just using opcode.
   *
   * <p>We overwrite the base view callback and only in the few payload instruction cases do we make
   * use of the parsed-instruction callbacks.
   */
  @Override
  public void onInstructionView(LirInstructionView view) {
    sizeEstimate += instructionSize(view.getOpcode(), view);
  }

  @Override
  public void onIntSwitch(EV unusedValue, IntSwitchPayload payload) {
    sizeEstimate +=
        DexPackedSwitch.SIZE
            + DexPackedSwitchPayload.SIZE
            + (2 * payload.keys.length)
            + (2 * payload.targets.length);
  }

  @Override
  public void onNewArrayFilledData(int elementWidth, long size, short[] data, EV unusedSrc) {
    sizeEstimate += DexFillArrayData.SIZE + DexFillArrayDataPayload.SIZE + 4 + data.length;
  }

  private int instructionSize(int opcode, LirInstructionView view) {
    switch (opcode) {
      case TABLESWITCH:
      case NEWARRAYFILLEDDATA:
        // The payload instructions use the "parsed callback" to compute the payloads.
        super.onInstructionView(view);
        // The full size is added by the callbacks so return zero here.
        return 0;

      case ACONST_NULL:
      case ICONST_M1:
      case ICONST_0:
      case ICONST_1:
      case ICONST_2:
      case ICONST_3:
      case ICONST_4:
      case ICONST_5:
        return DexConst4.SIZE;

      case LCONST_0:
      case LCONST_1:
      case FCONST_0:
      case FCONST_1:
      case FCONST_2:
      case DCONST_0:
      case DCONST_1:
        return DexConstWide16.SIZE;

      case LDC:
        // Most of the const loads are the same size (2).
        return DexConstString.SIZE;

      case IALOAD:
      case LALOAD:
      case FALOAD:
      case DALOAD:
      case AALOAD:
      case BALOAD:
      case CALOAD:
      case SALOAD:
        // The loads are all size 2.
        return DexAget.SIZE;

      case IASTORE:
      case LASTORE:
      case FASTORE:
      case DASTORE:
      case AASTORE:
      case BASTORE:
      case CASTORE:
      case SASTORE:
        // The loads are all size 2.
        return DexAput.SIZE;

      case IADD:
      case LADD:
      case FADD:
      case DADD:
      case ISUB:
      case LSUB:
      case FSUB:
      case DSUB:
      case IMUL:
      case LMUL:
      case FMUL:
      case DMUL:
      case IDIV:
      case LDIV:
      case FDIV:
      case DDIV:
      case IREM:
      case LREM:
      case FREM:
      case DREM:
        // The binary ops are all size 2.
        return DexBase2Format.SIZE;

      case INEG:
      case LNEG:
      case FNEG:
      case DNEG:
        // The negs are all size 1.
        return DexBase1Format.SIZE;

      case ISHL:
      case LSHL:
      case ISHR:
      case LSHR:
      case IUSHR:
      case LUSHR:
      case IAND:
      case LAND:
      case IOR:
      case LOR:
      case IXOR:
      case LXOR:
        // The binary ops are all size 2.
        return DexBase2Format.SIZE;

      case I2L:
      case I2F:
      case I2D:
      case L2I:
      case L2F:
      case L2D:
      case F2I:
      case F2L:
      case F2D:
      case D2I:
      case D2L:
      case D2F:
      case I2B:
      case I2C:
      case I2S:
        // Number conversions are all size 1.
        return DexBase1Format.SIZE;

      case LCMP:
      case FCMPL:
      case FCMPG:
      case DCMPL:
      case DCMPG:
        return DexBase2Format.SIZE;

      case IFEQ:
      case IFNE:
      case IFLT:
      case IFGE:
      case IFGT:
      case IFLE:
      case IF_ICMPEQ:
      case IF_ICMPNE:
      case IF_ICMPLT:
      case IF_ICMPGE:
      case IF_ICMPGT:
      case IF_ICMPLE:
      case IF_ACMPEQ:
      case IF_ACMPNE:
        return DexBase2Format.SIZE;

      case GOTO:
        return DexGoto.SIZE;

      case ARETURN:
      case RETURN:
        return DexBase1Format.SIZE;

      case GETSTATIC:
      case PUTSTATIC:
      case GETFIELD:
      case PUTFIELD:
        return DexBase2Format.SIZE;

      case INVOKEVIRTUAL:
      case INVOKESPECIAL:
      case INVOKESTATIC:
      case INVOKEINTERFACE:
        return DexBase3Format.SIZE;

      case INVOKEDYNAMIC:
        return DexInvokeCustom.SIZE;

      case NEW:
        return DexNewInstance.SIZE;
      case NEWARRAY:
        return DexNewArray.SIZE;
      case ARRAYLENGTH:
        return DexArrayLength.SIZE;
      case ATHROW:
        return DexThrow.SIZE;
      case CHECKCAST:
        return DexCheckCast.SIZE;
      case INSTANCEOF:
        return DexInstanceOf.SIZE;
      case MONITORENTER:
        return DexMonitorEnter.SIZE;
      case MONITOREXIT:
        return DexMonitorExit.SIZE;
      case MULTIANEWARRAY:
        return DexFilledNewArray.SIZE;

      case IFNULL:
      case IFNONNULL:
        return DexBase1Format.SIZE;

        // Non-CF instructions.
      case ICONST:
      case LCONST:
      case FCONST:
      case DCONST:
        return DexConst16.SIZE;

      case INVOKESTATIC_ITF:
      case INVOKEDIRECT:
      case INVOKEDIRECT_ITF:
      case INVOKESUPER:
      case INVOKESUPER_ITF:
        return DexBase3Format.SIZE;

      case DEBUGPOS:
        // Often debug positions will be associated with instructions so assume size 0.
        return 0;

      case PHI:
        // Assume a move per phi.
        return DexMove.SIZE;

      case FALLTHROUGH:
        // Hopefully fallthrough points will not materialize as instructions.
        return 0;

      case MOVEEXCEPTION:
        return DexMoveException.SIZE;

      case DEBUGLOCALWRITE:
        return DexMove.SIZE;

      case INVOKENEWARRAY:
        return DexFilledNewArray.SIZE;

      case ITEMBASEDCONSTSTRING:
        return DexConstString.SIZE;

      case NEWUNBOXEDENUMINSTANCE:
        return DexConst16.SIZE;

      case INOT:
        return DexNotInt.SIZE;
      case LNOT:
        return DexNotLong.SIZE;

      case DEBUGLOCALREAD:
        // These reads do not materialize after register allocation.
        return 0;

      case INITCLASS:
        return DexSget.SIZE;

      case INVOKEPOLYMORPHIC:
        return DexBase3Format.SIZE;

      case RECORDFIELDVALUES:
        // Rewritten to new-array in DEX.
        return DexNewArray.SIZE;

      case CHECKCAST_SAFE:
      case CHECKCAST_IGNORE_COMPAT:
        return DexCheckCast.SIZE;

      case CONSTCLASS_IGNORE_COMPAT:
        return DexConstClass.SIZE;

      default:
        throw new Unreachable("Unexpected LIR opcode: " + opcode);
    }
  }
}
