// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf_segments;

import com.google.classlib.events.InstructionHandler;
import com.google.classlib.model.ClassInfo;
import com.google.classlib.model.ConstantPoolTag;
import com.google.classlib.model.FieldrefInfo;
import com.google.classlib.model.InterfaceMethodrefInfo;
import com.google.classlib.model.Item;
import com.google.classlib.model.KnownAttribute;
import com.google.classlib.model.MethodrefInfo;
import com.google.classlib.model.NameAndTypeInfo;
import com.google.classlib.model.StringInfo;
import com.google.classlib.model.VerificationTypeInfo;
import com.google.classlib.pool.ResolvingClassEventHandler;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

public class MeasureClassEventHandler extends ResolvingClassEventHandler {

  private final Metrics metrics;

  public MeasureClassEventHandler(Metrics metrics) {
    super(new InstHandler(metrics));
    this.metrics = metrics;
    // ClassFile {
    //     u4             magic;
    //     u2             minor_version;
    //     u2             major_version;
    //     u2             constant_pool_count;
    //     cp_info        constant_pool[constant_pool_count-1];
    //     u2             access_flags;
    //     u2             this_class;
    //     u2             super_class;
    //     u2             interfaces_count;
    //     u2             interfaces[interfaces_count];
    //     u2             fields_count;
    //     field_info     fields[fields_count];
    //     u2             methods_count;
    //     method_info    methods[methods_count];
    //     u2             attributes_count;
    //     attribute_info attributes[attributes_count];
    // }
    metrics.classFile.increment(1, 24);
  }

  @Override
  public void handleInterfaces(int[] interfaces) {
    super.handleInterfaces(interfaces);
    this.metrics.interfaces.increment(interfaces.length, interfaces.length * 2);
  }

  @Override
  public void handleAttributeInfo(int attributeNameIndex, Callable<byte[]> info) {
    super.handleAttributeInfo(attributeNameIndex, info);
    // attribute_info {
    //     u2 attribute_name_index;
    //     u4 attribute_length;
    //     u1 info[attribute_length];
    // }
    try {
      String type = getPool()[attributeNameIndex].toString();
      int size = info.call().length + 6;
      switch (type) {
        case "SourceFile":
          metrics.otherClassAttributes.increment(1, size);
          break;
        case "BootstrapMethods":
          metrics.bootstrapMethodAttributes.increment(1, size);
          break;
        case "InnerClasses":
          metrics.innerClasses.increment(1, size);
          break;
        case "LineNumberTable":
          metrics.lineNumberTableEntries.increment(1, size);
          break;
        case "LocalVariableTable":
          metrics.localVariableTable.increment(1, size);
          break;
        case "StackMap":
          // StackMapEntries are counted separately.
          metrics.stackMapTable.increment(0, size);
          break;
        default:
          metrics.otherClassAttributes.increment(1, size);
          break;
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void endConstantPool() {
    super.endConstantPool();
    for (int i = 1; i < getPool().length; i++) {
      Object object = getPool()[i];
      if (object instanceof ClassInfo || object instanceof StringInfo) {
        metrics.constantPool.increment(1, 3);
      } else if (object instanceof FieldrefInfo
          || object instanceof MethodrefInfo
          || object instanceof InterfaceMethodrefInfo
          || object instanceof Integer
          || object instanceof Float
          || object instanceof NameAndTypeInfo) {
        metrics.constantPool.increment(1, 5);
      } else if (object instanceof Long || object instanceof Double) {
        metrics.constantPool.increment(1, 9);
        // Skip next entry.
        i++;
      } else {
        // This is either a String, or object is null, constituting Constant_MethodHandle,
        // Constant_MethodType or InvokeDynamic. All handled below in other handlers.
        assert object instanceof String || object == null;
      }
    }
  }

  @Override
  public void handleConstant(ConstantPoolTag tag, int index, int length, String utf8) {
    super.handleConstant(tag, index, length, utf8);
    metrics.constantPool.increment(1, length + 3);
  }

  @Override
  public void handleConstantMethodHandle(
      ConstantPoolTag tag, int index, int referenceKind, int referenceIndex) {
    super.handleConstantMethodHandle(tag, index, referenceKind, referenceIndex);
    metrics.constantPool.increment(1, 4);
  }

  @Override
  public void handleConstantMethodType(ConstantPoolTag tag, int index, int descriptorIndex) {
    super.handleConstantMethodType(tag, index, descriptorIndex);
    metrics.constantPool.increment(1, 3);
  }

  @Override
  public void handleConstantInvokeDynamic(
      ConstantPoolTag tag, int index, int bootstrapMethodAttributeIndex, int nameAndTypeIndex) {
    super.handleConstantInvokeDynamic(tag, index, bootstrapMethodAttributeIndex, nameAndTypeIndex);
    metrics.constantPool.increment(1, 5);
  }

  @Override
  public void beginFieldInfo(
      int accessFlags, int nameIndex, int descriptorIndex, int attributesCount) {
    // field_info {
    //     u2             access_flags;
    //     u2             name_index;
    //     u2             descriptor_index;
    //     u2             attributes_count;
    //     attribute_info attributes[attributes_count];
    // }
    long infoSize = 2 + 2 + 2 + 2;
    metrics.fieldInfo.increment(1, infoSize);
  }

  @Override
  public void beginMethodInfo(
      int accessFlags, int nameIndex, int descriptorIndex, int attributesCount) {
    // method_info {
    //     u2             access_flags;
    //     u2             name_index;
    //     u2             descriptor_index;
    //     u2             attributes_count;
    //     attribute_info attributes[attributes_count];
    // }
    long infoSize = 2 + 2 + 2 + 2;
    metrics.methodInfo.increment(1, infoSize);
  }

  @Override
  public Set<KnownAttribute> getHandledAttributes() {
    Set<KnownAttribute> known = new HashSet<>();
    known.add(KnownAttribute.Code);
    known.add(KnownAttribute.StackMapTable);
    return known;
  }

  @Override
  public void beginCodeAttribute(int maxStack, int maxLocals, long codeLength) {
    super.beginCodeAttribute(maxStack, maxLocals, codeLength);
    // Code_attribute {
    //     u2 attribute_name_index;
    //     u4 attribute_length;
    //     u2 max_stack;
    //     u2 max_locals;
    //     u4 code_length;
    //     u1 code[code_length];
    //     u2 exception_table_length;
    //     {   u2 start_pc;
    //         u2 end_pc;
    //         u2 handler_pc;
    //         u2 catch_type;
    //     } exception_table[exception_table_length];
    //     u2 attributes_count;
    //     attribute_info attributes[attributes_count];
    // }
    long codeSize = 2 + 4 + 2 + 2 + 4 + codeLength + 2 + 2;
    metrics.code.increment(1, codeSize);
    metrics.maxLocals.increment(1, maxLocals);
    metrics.maxStacks.increment(1, maxStack);
  }

  @Override
  public void handleExceptionTableEntry(int startPc, int endPc, int handlerPc, int catchType) {
    super.handleExceptionTableEntry(startPc, endPc, handlerPc, catchType);
    metrics.exceptionTable.increment(1, 8);
  }

  @Override
  public void beginStackMapTableAttribute(int numberOfEntries) {
    super.beginStackMapTableAttribute(numberOfEntries);
    metrics.stackMapTable.increment(1, 2 + 4 + 2);
  }

  private int sizeOfVerificationTypeInfo(VerificationTypeInfo info) {
    if (info.getItem() == Item.ITEM_Object || info.getItem() == Item.ITEM_Uninitialized) {
      return 3;
    } else {
      return 1;
    }
  }

  @Override
  public void handleSameFrame(int offsetDelta) {
    super.handleSameFrame(offsetDelta);
    metrics.stackmapTableOtherEntries.increment(1, 1);
  }

  @Override
  public void handleSameLocals1StackItemFrame(int offsetDelta, VerificationTypeInfo stack) {
    super.handleSameLocals1StackItemFrame(offsetDelta, stack);
    metrics.stackmapTableOtherEntries.increment(1, 1 + sizeOfVerificationTypeInfo(stack));
  }

  @Override
  public void handleSameLocals1StackItemFrameExtended(int offsetDelta, VerificationTypeInfo stack) {
    super.handleSameLocals1StackItemFrameExtended(offsetDelta, stack);
    metrics.stackmapTableOtherEntries.increment(1, 3 + sizeOfVerificationTypeInfo(stack));
  }

  @Override
  public void handleChopFrame(int offsetDelta, int chopLocals) {
    super.handleChopFrame(offsetDelta, chopLocals);
    metrics.stackmapTableOtherEntries.increment(1, 3);
  }

  @Override
  public void handleSameFrameExtended(int offsetDelta) {
    super.handleSameFrameExtended(offsetDelta);
    metrics.stackmapTableOtherEntries.increment(1, 3);
  }

  @Override
  public void handleAppendFrame(int offsetDelta, VerificationTypeInfo[] locals) {
    super.handleAppendFrame(offsetDelta, locals);
    int size = 3;
    for (VerificationTypeInfo type : locals) {
      size += sizeOfVerificationTypeInfo(type);
    }
    metrics.stackmapTableOtherEntries.increment(1, size);
  }

  @Override
  public void handleFullFrame(
      int offsetDelta, VerificationTypeInfo[] locals, VerificationTypeInfo[] stack) {
    super.handleFullFrame(offsetDelta, locals, stack);
    // full_frame {
    //     u1 frame_type = FULL_FRAME; /* 255 */
    //     u2 offset_delta;
    //     u2 number_of_locals;
    //     verification_type_info locals[number_of_locals];
    //     u2 number_of_stack_items;
    //     verification_type_info stack[number_of_stack_items];
    // }
    int size = 7;
    for (VerificationTypeInfo type : locals) {
      size += sizeOfVerificationTypeInfo(type);
    }
    for (VerificationTypeInfo type : stack) {
      size += sizeOfVerificationTypeInfo(type);
    }
    metrics.stackMapTableFullFrameEntries.increment(1, size);
  }


  // The instruction handler allows for measuring size on the instruction level. At this point we
  // are calculating the entire code size in beginCodeAttribute. However, to keep track of our how
  // well our load-store insertion works we are tracking the number of loads and stores inserted.
  private static class InstHandler implements InstructionHandler<Integer> {

    private final Metrics metrics;

    public InstHandler(Metrics metrics) {
      this.metrics = metrics;
    }

    @Override
    public Integer aaload() throws NullPointerException, ArrayIndexOutOfBoundsException {
      metrics.loads.increment(1, 2);
      return null;
    }

    @Override
    public Integer aastore()
        throws NullPointerException, ArrayIndexOutOfBoundsException, ArrayStoreException {
      metrics.stores.increment(1, 2);
      return null;
    }

    @Override
    public Integer aconstNull() {
      return null;
    }

    @Override
    public Integer aload(int index) {
      metrics.loads.increment(1, 3);
      return null;
    }

    @Override
    public Integer aloadN(int opcode) {
      metrics.loads.increment(1, 2);
      return null;
    }

    @Override
    public Integer anewarray(int index) throws NegativeArraySizeException {
      return null;
    }

    @Override
    public Integer areturn() throws IllegalMonitorStateException {
      return null;
    }

    @Override
    public Integer arraylength() throws NullPointerException {
      return null;
    }

    @Override
    public Integer astore(int index) {
      metrics.stores.increment(1, 3);
      return null;
    }

    @Override
    public Integer astoreN(int opcode) {
      metrics.stores.increment(1, 2);
      return null;
    }

    @Override
    public Integer athrow() throws NullPointerException, IllegalMonitorStateException {
      return null;
    }

    @Override
    public Integer baload() throws NullPointerException, ArrayIndexOutOfBoundsException {
      metrics.loads.increment(1, 2);
      return null;
    }

    @Override
    public Integer bastore() throws NullPointerException, ArrayIndexOutOfBoundsException {
      metrics.stores.increment(1, 2);
      return null;
    }

    @Override
    public Integer bipush(byte byteValue) {
      return null;
    }

    @Override
    public Integer caload() throws NullPointerException, ArrayIndexOutOfBoundsException {
      metrics.loads.increment(1, 2);
      return null;
    }

    @Override
    public Integer castore() throws NullPointerException, ArrayIndexOutOfBoundsException {
      metrics.stores.increment(1, 2);
      return null;
    }

    @Override
    public Integer checkcast(int index) throws ClassCastException {
      return null;
    }

    @Override
    public Integer d2f() {
      return null;
    }

    @Override
    public Integer d2i() {
      return null;
    }

    @Override
    public Integer d2l() {
      return null;
    }

    @Override
    public Integer dadd() {
      return null;
    }

    @Override
    public Integer daload() throws NullPointerException, ArrayIndexOutOfBoundsException {
      metrics.loads.increment(1, 2);
      return null;
    }

    @Override
    public Integer dastore() throws NullPointerException, ArrayIndexOutOfBoundsException {
      metrics.stores.increment(1, 2);
      return null;
    }

    @Override
    public Integer dcmp(int opcode) {
      return null;
    }

    @Override
    public Integer dconstD(int opcode) {
      return null;
    }

    @Override
    public Integer ddiv() {
      return null;
    }

    @Override
    public Integer dload(int index) {
      metrics.loads.increment(1, 3);
      return null;
    }

    @Override
    public Integer dloadN(int opcode) {
      metrics.loads.increment(1, 2);
      return null;
    }

    @Override
    public Integer dmul() {
      return null;
    }

    @Override
    public Integer dneg() {
      return null;
    }

    @Override
    public Integer drem() {
      return null;
    }

    @Override
    public Integer dreturn() throws IllegalMonitorStateException {
      return null;
    }

    @Override
    public Integer dstore(int index) {
      metrics.stores.increment(1, 3);
      return null;
    }

    @Override
    public Integer dstoreN(int opcode) {
      metrics.stores.increment(1, 2);
      return null;
    }

    @Override
    public Integer dsub() {
      return null;
    }

    @Override
    public Integer dup() {
      return null;
    }

    @Override
    public Integer dupX1() {
      return null;
    }

    @Override
    public Integer dupX2() {
      return null;
    }

    @Override
    public Integer dup2() {
      return null;
    }

    @Override
    public Integer dup2X1() {
      return null;
    }

    @Override
    public Integer dup2X2() {
      return null;
    }

    @Override
    public Integer f2d() {
      return null;
    }

    @Override
    public Integer f2i() {
      return null;
    }

    @Override
    public Integer f2l() {
      return null;
    }

    @Override
    public Integer fadd() {
      return null;
    }

    @Override
    public Integer faload() throws NullPointerException, ArrayIndexOutOfBoundsException {
      metrics.loads.increment(1, 2);
      return null;
    }

    @Override
    public Integer fastore() throws NullPointerException, ArrayIndexOutOfBoundsException {
      metrics.stores.increment(1, 2);
      return null;
    }

    @Override
    public Integer fcmp(int opcode) {
      return null;
    }

    @Override
    public Integer fconstF(int opcode) {
      return null;
    }

    @Override
    public Integer fdiv() {
      return null;
    }

    @Override
    public Integer fload(int index) {
      metrics.loads.increment(1, 3);
      return null;
    }

    @Override
    public Integer floadN(int opcode) {
      metrics.loads.increment(1, 2);
      return null;
    }

    @Override
    public Integer fmul() {
      return null;
    }

    @Override
    public Integer fneg() {
      return null;
    }

    @Override
    public Integer frem() {
      return null;
    }

    @Override
    public Integer freturn() throws IllegalMonitorStateException {
      return null;
    }

    @Override
    public Integer fstore(int index) {
      metrics.stores.increment(1, 3);
      return null;
    }

    @Override
    public Integer fstoreN(int opcode) {
      metrics.stores.increment(1, 2);
      return null;
    }

    @Override
    public Integer fsub() {
      return null;
    }

    @Override
    public Integer getfield(int index) throws IncompatibleClassChangeError, NullPointerException {
      return null;
    }

    @Override
    public Integer getstatic(int index) throws IncompatibleClassChangeError, Error {
      return null;
    }

    @Override
    public Integer gotoInstruction(short branch) {
      metrics.jumps.increment(1, 4);
      return null;
    }

    @Override
    public Integer gotoW(int branch) {
      metrics.jumps.increment(1, 6);
      return null;
    }

    @Override
    public Integer i2b() {
      return null;
    }

    @Override
    public Integer i2c() {
      return null;
    }

    @Override
    public Integer i2d() {
      return null;
    }

    @Override
    public Integer i2f() {
      return null;
    }

    @Override
    public Integer i2l() {
      return null;
    }

    @Override
    public Integer i2s() {
      return null;
    }

    @Override
    public Integer iadd() {
      return null;
    }

    @Override
    public Integer iaload() throws NullPointerException, ArrayIndexOutOfBoundsException {
      metrics.loads.increment(1, 2);
      return null;
    }

    @Override
    public Integer iand() {
      return null;
    }

    @Override
    public Integer iastore() throws NullPointerException, ArrayIndexOutOfBoundsException {
      metrics.stores.increment(1, 2);
      return null;
    }

    @Override
    public Integer iconstI(int opcode) {
      return null;
    }

    @Override
    public Integer idiv() throws ArithmeticException {
      return null;
    }

    @Override
    public Integer ifAcmpeq(short branch) {
      metrics.jumps.increment(1, 4);
      return null;
    }

    @Override
    public Integer ifAcmpne(short branch) {
      metrics.jumps.increment(1, 4);
      return null;
    }

    @Override
    public Integer ifIcmpeq(short branch) {
      metrics.jumps.increment(1, 4);
      return null;
    }

    @Override
    public Integer ifIcmpne(short branch) {
      metrics.jumps.increment(1, 4);
      return null;
    }

    @Override
    public Integer ifIcmplt(short branch) {
      metrics.jumps.increment(1, 4);
      return null;
    }

    @Override
    public Integer ifIcmpge(short branch) {
      metrics.jumps.increment(1, 4);
      return null;
    }

    @Override
    public Integer ifIcmpgt(short branch) {
      metrics.jumps.increment(1, 4);
      return null;
    }

    @Override
    public Integer ifIcmple(short branch) {
      metrics.jumps.increment(1, 4);
      return null;
    }

    @Override
    public Integer ifeq(short branch) {
      metrics.jumps.increment(1, 4);
      return null;
    }

    @Override
    public Integer ifne(short branch) {
      metrics.jumps.increment(1, 4);
      return null;
    }

    @Override
    public Integer iflt(short branch) {
      metrics.jumps.increment(1, 4);
      return null;
    }

    @Override
    public Integer ifge(short branch) {
      metrics.jumps.increment(1, 4);
      return null;
    }

    @Override
    public Integer ifgt(short branch) {
      metrics.jumps.increment(1, 4);
      return null;
    }

    @Override
    public Integer ifle(short branch) {
      metrics.jumps.increment(1, 4);
      return null;
    }

    @Override
    public Integer ifnonnull(short branch) {
      metrics.jumps.increment(1, 4);
      return null;
    }

    @Override
    public Integer ifnull(short branch) {
      metrics.jumps.increment(1, 4);
      return null;
    }

    @Override
    public Integer iinc(int index, short constValue) {
      return null;
    }

    @Override
    public Integer iload(int index) {
      metrics.loads.increment(1, 3);
      return null;
    }

    @Override
    public Integer iloadN(int opcode) {
      metrics.loads.increment(1, 2);
      return null;
    }

    @Override
    public Integer imul() {
      return null;
    }

    @Override
    public Integer ineg() {
      return null;
    }

    @Override
    public Integer instanceofInstruction(int index) {
      return null;
    }

    @Override
    public Integer invokedynamic(int index, short zero0, short zero1) {
      return null;
    }

    @Override
    public Integer invokeinterface(int index, short count, short zero)
        throws AbstractMethodError, NullPointerException, IncompatibleClassChangeError,
            IllegalAccessError, UnsatisfiedLinkError {
      return null;
    }

    @Override
    public Integer invokespecial(int index)
        throws AbstractMethodError, NullPointerException, UnsatisfiedLinkError {
      return null;
    }

    @Override
    public Integer invokestatic(int index) throws Error, UnsatisfiedLinkError {
      return null;
    }

    @Override
    public Integer invokevirtual(int index)
        throws NullPointerException, AbstractMethodError, UnsatisfiedLinkError {
      return null;
    }

    @Override
    public Integer ior() {
      return null;
    }

    @Override
    public Integer irem() throws ArithmeticException {
      return null;
    }

    @Override
    public Integer ireturn() throws IllegalMonitorStateException {
      return null;
    }

    @Override
    public Integer ishl() {
      return null;
    }

    @Override
    public Integer ishr() {
      return null;
    }

    @Override
    public Integer istore(int index) {
      metrics.stores.increment(1, 3);
      return null;
    }

    @Override
    public Integer istoreN(int opcode) {
      metrics.stores.increment(1, 2);
      return null;
    }

    @Override
    public Integer isub() {
      return null;
    }

    @Override
    public Integer iushr() {
      return null;
    }

    @Override
    public Integer ixor() {
      return null;
    }

    @Override
    public Integer jsr(short branch) {
      return null;
    }

    @Override
    public Integer jsrW(int branch) {
      return null;
    }

    @Override
    public Integer l2d() {
      return null;
    }

    @Override
    public Integer l2f() {
      return null;
    }

    @Override
    public Integer l2i() {
      return null;
    }

    @Override
    public Integer ladd() {
      return null;
    }

    @Override
    public Integer laload() throws NullPointerException, ArrayIndexOutOfBoundsException {
      metrics.loads.increment(1, 2);
      return null;
    }

    @Override
    public Integer land() {
      return null;
    }

    @Override
    public Integer lastore() throws NullPointerException, ArrayIndexOutOfBoundsException {
      metrics.stores.increment(1, 2);
      return null;
    }

    @Override
    public Integer lcmp() {
      return null;
    }

    @Override
    public Integer lconstL(int opcode) {
      return null;
    }

    @Override
    public Integer ldc(short index) {
      return null;
    }

    @Override
    public Integer ldcW(int index) {
      return null;
    }

    @Override
    public Integer ldc2W(int index) {
      return null;
    }

    @Override
    public Integer ldiv() throws ArithmeticException {
      return null;
    }

    @Override
    public Integer lload(int index) {
      metrics.loads.increment(1, 3);
      return null;
    }

    @Override
    public Integer lloadN(int opcode) {
      metrics.loads.increment(1, 2);
      return null;
    }

    @Override
    public Integer lmul() {
      return null;
    }

    @Override
    public Integer lneg() {
      return null;
    }

    @Override
    public Integer lookupswitch(int defaultValue, int npairs, int... matchOffsetPairs) {
      metrics.jumps.increment(1, 2 + 8 + matchOffsetPairs.length * 8);
      return null;
    }

    @Override
    public Integer lor() {
      return null;
    }

    @Override
    public Integer lrem() throws ArithmeticException {
      return null;
    }

    @Override
    public Integer lreturn() throws IllegalMonitorStateException {
      return null;
    }

    @Override
    public Integer lshl() {
      return null;
    }

    @Override
    public Integer lshr() {
      return null;
    }

    @Override
    public Integer lstore(int index) {
      metrics.stores.increment(1, 3);
      return null;
    }

    @Override
    public Integer lstoreN(int opcode) {
      metrics.stores.increment(1, 2);
      return null;
    }

    @Override
    public Integer lsub() {
      return null;
    }

    @Override
    public Integer lushr() {
      return null;
    }

    @Override
    public Integer lxor() {
      return null;
    }

    @Override
    public Integer monitorenter() throws NullPointerException {
      return null;
    }

    @Override
    public Integer monitorexit() throws NullPointerException, IllegalMonitorStateException {
      return null;
    }

    @Override
    public Integer multianewarray(int index, short dimensions) throws NegativeArraySizeException {
      return null;
    }

    @Override
    public Integer newInstruction(int index) throws Error {
      return null;
    }

    @Override
    public Integer newarray(short atype) throws NegativeArraySizeException {
      return null;
    }

    @Override
    public Integer nop() {
      return null;
    }

    @Override
    public Integer pop() {
      return null;
    }

    @Override
    public Integer pop2() {
      return null;
    }

    @Override
    public Integer putfield(int index) throws NullPointerException {
      return null;
    }

    @Override
    public Integer putstatic(int index) throws Error {
      return null;
    }

    @Override
    public Integer ret(int index) {
      return null;
    }

    @Override
    public Integer returnInstruction() throws IllegalMonitorStateException {
      return null;
    }

    @Override
    public Integer saload() throws NullPointerException, ArrayIndexOutOfBoundsException {
      metrics.loads.increment(1, 2);
      return null;
    }

    @Override
    public Integer sastore() throws NullPointerException, ArrayIndexOutOfBoundsException {
      metrics.stores.increment(1, 2);
      return null;
    }

    @Override
    public Integer sipush(short value) {
      return null;
    }

    @Override
    public Integer swap() {
      return null;
    }

    @Override
    public Integer tableswitch(int defaultValue, int low, int high, int... jumpoffsets) {
      metrics.jumps.increment(1, 2 + 16 + jumpoffsets.length * 4);
      return null;
    }

    @Override
    public Integer wide() {
      return null;
    }

    @Override
    public Integer impdep1() {
      return null;
    }

    @Override
    public Integer impdep2() {
      return null;
    }

    @Override
    public Integer breakpoint() {
      return null;
    }

    @Override
    public Integer unknown(int opcode) {
      return null;
    }

    @Override
    public void setPc(long pc) {}

    @Override
    public void handleParseError(String message) {}
  }
}
