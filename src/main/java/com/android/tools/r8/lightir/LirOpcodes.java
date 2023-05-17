// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.errors.Unreachable;

/**
 * Constants related to LIR.
 *
 * <p>The constants generally follow the bytecode values as defined by the classfile format.
 */
public interface LirOpcodes {

  static boolean isOneByteInstruction(int opcode) {
    assert opcode >= ACONST_NULL;
    return opcode <= DCONST_1
        || opcode == RETURN
        || opcode == DEBUGPOS
        || opcode == FALLTHROUGH
        || opcode == DEBUGLOCALREAD;
  }

  // Instructions maintaining the same opcode as defined in CF.
  // int NOP = 0;
  int ACONST_NULL = 1;
  int ICONST_M1 = 2;
  int ICONST_0 = 3;
  int ICONST_1 = 4;
  int ICONST_2 = 5;
  int ICONST_3 = 6;
  int ICONST_4 = 7;
  int ICONST_5 = 8;
  int LCONST_0 = 9;
  int LCONST_1 = 10;
  int FCONST_0 = 11;
  int FCONST_1 = 12;
  int FCONST_2 = 13;
  int DCONST_0 = 14;
  int DCONST_1 = 15;
  // int BIPUSH = 16;
  // int SIPUSH = 17;
  int LDC = 18;
  // int ILOAD = 21;
  // int LLOAD = 22;
  // int FLOAD = 23;
  // int DLOAD = 24;
  // int ALOAD = 25;
  int IALOAD = 46;
  int LALOAD = 47;
  int FALOAD = 48;
  int DALOAD = 49;
  int AALOAD = 50;
  int BALOAD = 51;
  int CALOAD = 52;
  int SALOAD = 53;
  // int ISTORE = 54;
  // int LSTORE = 55;
  // int FSTORE = 56;
  // int DSTORE = 57;
  // int ASTORE = 58;
  int IASTORE = 79;
  int LASTORE = 80;
  int FASTORE = 81;
  int DASTORE = 82;
  int AASTORE = 83;
  int BASTORE = 84;
  int CASTORE = 85;
  int SASTORE = 86;
  // int POP = 87;
  // int POP2 = 88;
  // int DUP = 89;
  // int DUP_X1 = 90;
  // int DUP_X2 = 91;
  // int DUP2 = 92;
  // int DUP2_X1 = 93;
  // int DUP2_X2 = 94;
  // int SWAP = 95;
  int IADD = 96;
  int LADD = 97;
  int FADD = 98;
  int DADD = 99;
  int ISUB = 100;
  int LSUB = 101;
  int FSUB = 102;
  int DSUB = 103;
  int IMUL = 104;
  int LMUL = 105;
  int FMUL = 106;
  int DMUL = 107;
  int IDIV = 108;
  int LDIV = 109;
  int FDIV = 110;
  int DDIV = 111;
  int IREM = 112;
  int LREM = 113;
  int FREM = 114;
  int DREM = 115;
  int INEG = 116;
  int LNEG = 117;
  int FNEG = 118;
  int DNEG = 119;
  int ISHL = 120;
  int LSHL = 121;
  int ISHR = 122;
  int LSHR = 123;
  int IUSHR = 124;
  int LUSHR = 125;
  int IAND = 126;
  int LAND = 127;
  int IOR = 128;
  int LOR = 129;
  int IXOR = 130;
  int LXOR = 131;
  // int IINC = 132;
  int I2L = 133;
  int I2F = 134;
  int I2D = 135;
  int L2I = 136;
  int L2F = 137;
  int L2D = 138;
  int F2I = 139;
  int F2L = 140;
  int F2D = 141;
  int D2I = 142;
  int D2L = 143;
  int D2F = 144;
  int I2B = 145;
  int I2C = 146;
  int I2S = 147;
  int LCMP = 148;
  int FCMPL = 149;
  int FCMPG = 150;
  int DCMPL = 151;
  int DCMPG = 152;
  int IFEQ = 153;
  int IFNE = 154;
  int IFLT = 155;
  int IFGE = 156;
  int IFGT = 157;
  int IFLE = 158;
  int IF_ICMPEQ = 159;
  int IF_ICMPNE = 160;
  int IF_ICMPLT = 161;
  int IF_ICMPGE = 162;
  int IF_ICMPGT = 163;
  int IF_ICMPLE = 164;
  int IF_ACMPEQ = 165;
  int IF_ACMPNE = 166;
  int GOTO = 167;
  // int JSR = 168;
  // int RET = 169;
  int TABLESWITCH = 170;
  int LOOKUPSWITCH = 171;
  // int IRETURN = 172;
  // int LRETURN = 173;
  // int FRETURN = 174;
  // int DRETURN = 175;
  // All value returns use areturn.
  int ARETURN = 176;
  // Void return.
  int RETURN = 177;
  int GETSTATIC = 178;
  int PUTSTATIC = 179;
  int GETFIELD = 180;
  int PUTFIELD = 181;
  int INVOKEVIRTUAL = 182;
  int INVOKESPECIAL = 183;
  int INVOKESTATIC = 184;
  int INVOKEINTERFACE = 185;
  int INVOKEDYNAMIC = 186;
  int NEW = 187;
  int NEWARRAY = 188;
  // All arrays use NEWARRAY and a type item pointer
  // int ANEWARRAY = 189;
  int ARRAYLENGTH = 190;
  int ATHROW = 191;
  int CHECKCAST = 192;
  int INSTANCEOF = 193;
  int MONITORENTER = 194;
  int MONITOREXIT = 195;
  int MULTIANEWARRAY = 197;
  int IFNULL = 198;
  int IFNONNULL = 199;

  // Non-CF instructions.
  int ICONST = 200;
  int LCONST = 201;
  int FCONST = 202;
  int DCONST = 203;
  int INVOKESTATIC_ITF = 204;
  int INVOKEDIRECT = 205;
  int INVOKEDIRECT_ITF = 206;
  int INVOKESUPER = 207;
  int INVOKESUPER_ITF = 208;
  int DEBUGPOS = 209;
  int PHI = 210;
  int FALLTHROUGH = 211;
  int MOVEEXCEPTION = 212;
  int DEBUGLOCALWRITE = 213;
  int INVOKENEWARRAY = 214;
  int NEWARRAYFILLEDDATA = 215;
  int ITEMBASEDCONSTSTRING = 216;
  int NEWUNBOXEDENUMINSTANCE = 217;
  int INOT = 218;
  int LNOT = 219;
  int DEBUGLOCALREAD = 220;
  int INITCLASS = 221;
  int INVOKEPOLYMORPHIC = 222;
  int RECORDFIELDVALUES = 223;

  static String toString(int opcode) {
    switch (opcode) {
        // case NOP: return "NOP";
      case ACONST_NULL:
        return "ACONST_NULL";
      case ICONST_M1:
        return "ICONST_M1";
      case ICONST_0:
        return "ICONST_0";
      case ICONST_1:
        return "ICONST_1";
      case ICONST_2:
        return "ICONST_2";
      case ICONST_3:
        return "ICONST_3";
      case ICONST_4:
        return "ICONST_4";
      case ICONST_5:
        return "ICONST_5";
      case LCONST_0:
        return "LCONST_0";
      case LCONST_1:
        return "LCONST_1";
      case FCONST_0:
        return "FCONST_0";
      case FCONST_1:
        return "FCONST_1";
      case FCONST_2:
        return "FCONST_2";
      case DCONST_0:
        return "DCONST_0";
      case DCONST_1:
        return "DCONST_1";
        // case BIPUSH: return "BIPUSH";
        // case SIPUSH: return "SIPUSH";
      case LDC:
        return "LDC";
        // case ILOAD: return "ILOAD";
        // case LLOAD: return "LLOAD";
        // case FLOAD: return "FLOAD";
        // case DLOAD: return "DLOAD";
        // case ALOAD: return "ALOAD";
      case IALOAD:
        return "IALOAD";
      case LALOAD:
        return "LALOAD";
      case FALOAD:
        return "FALOAD";
      case DALOAD:
        return "DALOAD";
      case AALOAD:
        return "AALOAD";
      case BALOAD:
        return "BALOAD";
      case CALOAD:
        return "CALOAD";
      case SALOAD:
        return "SALOAD";
        // case ISTORE: return "ISTORE";
        // case LSTORE: return "LSTORE";
        // case FSTORE: return "FSTORE";
        // case DSTORE: return "DSTORE";
        // case ASTORE: return "ASTORE";
      case IASTORE:
        return "IASTORE";
      case LASTORE:
        return "LASTORE";
      case FASTORE:
        return "FASTORE";
      case DASTORE:
        return "DASTORE";
      case AASTORE:
        return "AASTORE";
      case BASTORE:
        return "BASTORE";
      case CASTORE:
        return "CASTORE";
      case SASTORE:
        return "SASTORE";
        // case POP: return "POP";
        // case POP2: return "POP2";
        // case DUP: return "DUP";
        // case DUP_X1: return "DUP_X1";
        // case DUP_X2: return "DUP_X2";
        // case DUP2: return "DUP2";
        // case DUP2_X1: return "DUP2_X1";
        // case DUP2_X2: return "DUP2_X2";
        // case SWAP: return "SWAP";
      case IADD:
        return "IADD";
      case LADD:
        return "LADD";
      case FADD:
        return "FADD";
      case DADD:
        return "DADD";
      case ISUB:
        return "ISUB";
      case LSUB:
        return "LSUB";
      case FSUB:
        return "FSUB";
      case DSUB:
        return "DSUB";
      case IMUL:
        return "IMUL";
      case LMUL:
        return "LMUL";
      case FMUL:
        return "FMUL";
      case DMUL:
        return "DMUL";
      case IDIV:
        return "IDIV";
      case LDIV:
        return "LDIV";
      case FDIV:
        return "FDIV";
      case DDIV:
        return "DDIV";
      case IREM:
        return "IREM";
      case LREM:
        return "LREM";
      case FREM:
        return "FREM";
      case DREM:
        return "DREM";
      case INEG:
        return "INEG";
      case LNEG:
        return "LNEG";
      case FNEG:
        return "FNEG";
      case DNEG:
        return "DNEG";
      case ISHL:
        return "ISHL";
      case LSHL:
        return "LSHL";
      case ISHR:
        return "ISHR";
      case LSHR:
        return "LSHR";
      case IUSHR:
        return "IUSHR";
      case LUSHR:
        return "LUSHR";
      case IAND:
        return "IAND";
      case LAND:
        return "LAND";
      case IOR:
        return "IOR";
      case LOR:
        return "LOR";
      case IXOR:
        return "IXOR";
      case LXOR:
        return "LXOR";
        // case IINC: return "IINC";
      case I2L:
        return "I2L";
      case I2F:
        return "I2F";
      case I2D:
        return "I2D";
      case L2I:
        return "L2I";
      case L2F:
        return "L2F";
      case L2D:
        return "L2D";
      case F2I:
        return "F2I";
      case F2L:
        return "F2L";
      case F2D:
        return "F2D";
      case D2I:
        return "D2I";
      case D2L:
        return "D2L";
      case D2F:
        return "D2F";
      case I2B:
        return "I2B";
      case I2C:
        return "I2C";
      case I2S:
        return "I2S";
      case LCMP:
        return "LCMP";
      case FCMPL:
        return "FCMPL";
      case FCMPG:
        return "FCMPG";
      case DCMPL:
        return "DCMPL";
      case DCMPG:
        return "DCMPG";
      case IFEQ:
        return "IFEQ";
      case IFNE:
        return "IFNE";
      case IFLT:
        return "IFLT";
      case IFGE:
        return "IFGE";
      case IFGT:
        return "IFGT";
      case IFLE:
        return "IFLE";
      case IF_ICMPEQ:
        return "IF_ICMPEQ";
      case IF_ICMPNE:
        return "IF_ICMPNE";
      case IF_ICMPLT:
        return "IF_ICMPLT";
      case IF_ICMPGE:
        return "IF_ICMPGE";
      case IF_ICMPGT:
        return "IF_ICMPGT";
      case IF_ICMPLE:
        return "IF_ICMPLE";
      case IF_ACMPEQ:
        return "IF_ACMPEQ";
      case IF_ACMPNE:
        return "IF_ACMPNE";
      case GOTO:
        return "GOTO";
        // case JSR: return "JSR";
        // case RET: return "RET";
      case TABLESWITCH:
        return "TABLESWITCH";
      case LOOKUPSWITCH:
        return "LOOKUPSWITCH";
      case ARETURN:
        return "ARETURN";
      case RETURN:
        return "RETURN";
      case GETSTATIC:
        return "GETSTATIC";
      case PUTSTATIC:
        return "PUTSTATIC";
      case GETFIELD:
        return "GETFIELD";
      case PUTFIELD:
        return "PUTFIELD";
      case INVOKEVIRTUAL:
        return "INVOKEVIRTUAL";
      case INVOKESPECIAL:
        return "INVOKESPECIAL";
      case INVOKESTATIC:
        return "INVOKESTATIC";
      case INVOKEINTERFACE:
        return "INVOKEINTERFACE";
      case INVOKEDYNAMIC:
        return "INVOKEDYNAMIC";
      case NEW:
        return "NEW";
      case NEWARRAY:
        return "NEWARRAY";
      case ARRAYLENGTH:
        return "ARRAYLENGTH";
      case ATHROW:
        return "ATHROW";
      case CHECKCAST:
        return "CHECKCAST";
      case INSTANCEOF:
        return "INSTANCEOF";
      case MONITORENTER:
        return "MONITORENTER";
      case MONITOREXIT:
        return "MONITOREXIT";
      case MULTIANEWARRAY:
        return "MULTIANEWARRAY";
      case IFNULL:
        return "IFNULL";
      case IFNONNULL:
        return "IFNONNULL";

        // Non-CF instructions.
      case ICONST:
        return "ICONST";
      case LCONST:
        return "LCONST";
      case FCONST:
        return "FCONST";
      case DCONST:
        return "DCONST";
      case INVOKESTATIC_ITF:
        return "INVOKESTATIC_ITF";
      case INVOKEDIRECT:
        return "INVOKEDIRECT";
      case INVOKEDIRECT_ITF:
        return "INVOKEDIRECT_ITF";
      case INVOKESUPER:
        return "INVOKESUPER";
      case INVOKESUPER_ITF:
        return "INVOKESUPER_ITF";
      case DEBUGPOS:
        return "DEBUGPOS";
      case PHI:
        return "PHI";
      case FALLTHROUGH:
        return "FALLTHROUGH";
      case MOVEEXCEPTION:
        return "MOVEEXCEPTION";
      case DEBUGLOCALWRITE:
        return "DEBUGLOCALWRITE";
      case INVOKENEWARRAY:
        return "INVOKENEWARRAY";
      case NEWARRAYFILLEDDATA:
        return "NEWARRAYFILLEDDATA";
      case ITEMBASEDCONSTSTRING:
        return "ITEMBASEDCONSTSTRING";
      case NEWUNBOXEDENUMINSTANCE:
        return "NEWUNBOXEDENUMINSTANCE";
      case INOT:
        return "INOT";
      case LNOT:
        return "LNOT";
      case DEBUGLOCALREAD:
        return "DEBUGLOCALREAD";
      case INITCLASS:
        return "INITCLASS";
      case INVOKEPOLYMORPHIC:
        return "INVOKEPOLYMORPHIC";
      case RECORDFIELDVALUES:
        return "RECORDFIELDVALUES";

      default:
        throw new Unreachable("Unexpected LIR opcode: " + opcode);
    }
  }
}
