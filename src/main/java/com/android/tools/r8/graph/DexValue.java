// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

import com.android.tools.r8.dex.DexOutputBuffer;
import com.android.tools.r8.dex.FileWriter;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.ir.code.ConstInstruction;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.naming.dexitembasedstring.NameComputationInfo;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.EncodedValueUtils;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import java.util.Arrays;
import java.util.function.Consumer;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;

public abstract class DexValue extends DexItem implements StructuralItem<DexValue> {

  public enum DexValueKind {
    BYTE(0x00),
    SHORT(0x02),
    CHAR(0x03),
    INT(0x04),
    LONG(0x06),
    FLOAT(0x10),
    DOUBLE(0x11),
    METHOD_TYPE(0x15),
    METHOD_HANDLE(0x16),
    STRING(0x17),
    TYPE(0x18),
    FIELD(0x19),
    METHOD(0x1a),
    ENUM(0x1b),
    ARRAY(0x1c),
    ANNOTATION(0x1d),
    NULL(0x1e),
    BOOLEAN(0x1f);

    public static DexValueKind fromId(int id) {
      switch (id) {
        case 0x00:
          return BYTE;
        case 0x02:
          return SHORT;
        case 0x03:
          return CHAR;
        case 0x04:
          return INT;
        case 0x06:
          return LONG;
        case 0x10:
          return FLOAT;
        case 0x11:
          return DOUBLE;
        case 0x15:
          return METHOD_TYPE;
        case 0x16:
          return METHOD_HANDLE;
        case 0x17:
          return STRING;
        case 0x18:
          return TYPE;
        case 0x19:
          return FIELD;
        case 0x1a:
          return METHOD;
        case 0x1b:
          return ENUM;
        case 0x1c:
          return ARRAY;
        case 0x1d:
          return ANNOTATION;
        case 0x1e:
          return NULL;
        case 0x1f:
          return BOOLEAN;
        default:
          throw new Unreachable();
      }
    }

    private final byte b;

    DexValueKind(int b) {
      this.b = (byte) b;
    }

    byte toByte() {
      return b;
    }
  }

  @Override
  public DexValue self() {
    return this;
  }

  @Override
  public final StructuralMapping<DexValue> getStructuralMapping() {
    // DexValue is not generic at its base type (and can't as we use it as a polymorphic value),
    // so each concrete value must implement polymorphic accept functions. This base class
    // implements (most of) the polymorphic checks and concrete types implement the internal
    // variants for that type.
    throw new Unreachable();
  }

  @Override
  public final int acceptCompareTo(DexValue other, CompareToVisitor visitor) {
    // Order first on 'kind', only equal kinds then forward to the 'kind' specific internal compare.
    if (getValueKind() != other.getValueKind()) {
      return visitor.visitInt(getValueKind().toByte(), other.getValueKind().toByte());
    } else {
      return internalAcceptCompareTo(other, visitor);
    }
  }

  @Override
  public final void acceptHashing(HashingVisitor visitor) {
    // Always hash the 'kind' which ensures that raw values of different type are distinct.
    visitor.visitInt(getValueKind().toByte());
    internalAcceptHashing(visitor);
  }

  abstract int internalAcceptCompareTo(DexValue other, CompareToVisitor visitor);

  abstract void internalAcceptHashing(HashingVisitor visitor);

  public static final DexValue[] EMPTY_ARRAY = {};

  public abstract DexValueKind getValueKind();

  public boolean isDexItemBasedValueString() {
    return false;
  }

  public DexItemBasedValueString asDexItemBasedValueString() {
    return null;
  }

  public boolean isDexValueMethodHandle() {
    return false;
  }

  public DexValueMethodHandle asDexValueMethodHandle() {
    return null;
  }

  public boolean isDexValueMethodType() {
    return false;
  }

  public DexValueMethodType asDexValueMethodType() {
    return null;
  }

  public boolean isDexValueAnnotation() {
    return false;
  }

  public DexValueAnnotation asDexValueAnnotation() {
    return null;
  }

  public boolean isDexValueArray() {
    return false;
  }

  public DexValueArray asDexValueArray() {
    return null;
  }

  public boolean isDexValueBoolean() {
    return false;
  }

  public DexValueBoolean asDexValueBoolean() {
    return null;
  }

  public boolean isDexValueByte() {
    return false;
  }

  public DexValueByte asDexValueByte() {
    return null;
  }

  public boolean isDexValueDouble() {
    return false;
  }

  public DexValueDouble asDexValueDouble() {
    return null;
  }

  public boolean isDexValueChar() {
    return false;
  }

  public DexValueChar asDexValueChar() {
    return null;
  }

  public boolean isDexValueEnum() {
    return false;
  }

  public DexValueEnum asDexValueEnum() {
    return null;
  }

  public boolean isDexValueField() {
    return false;
  }

  public DexValueField asDexValueField() {
    return null;
  }

  public boolean isDexValueFloat() {
    return false;
  }

  public DexValueFloat asDexValueFloat() {
    return null;
  }

  public boolean isDexValueInt() {
    return false;
  }

  public DexValueInt asDexValueInt() {
    return null;
  }

  public boolean isDexValueLong() {
    return false;
  }

  public DexValueLong asDexValueLong() {
    return null;
  }

  public boolean isDexValueMethod() {
    return false;
  }

  public DexValueMethod asDexValueMethod() {
    return null;
  }

  public boolean isDexValueNull() {
    return false;
  }

  public DexValueNull asDexValueNull() {
    return null;
  }

  public boolean isDexValueNumber() {
    return false;
  }

  public DexValueNumber asDexValueNumber() {
    return null;
  }

  public boolean isDexValueShort() {
    return false;
  }

  public DexValueShort asDexValueShort() {
    return null;
  }

  public boolean isDexValueString() {
    return false;
  }

  public DexValueString asDexValueString() {
    return null;
  }

  public boolean isDexValueType() {
    return false;
  }

  public DexValueType asDexValueType() {
    return null;
  }

  public boolean isNestedDexValue() {
    return false;
  }

  public abstract AbstractValue toAbstractValue(AbstractValueFactory factory);

  public static DexValue fromAsmBootstrapArgument(
      Object value, JarApplicationReader application, DexType clazz) {
    if (value instanceof Integer) {
      return DexValue.DexValueInt.create((Integer) value);
    } else if (value instanceof Long) {
      return DexValue.DexValueLong.create((Long) value);
    } else if (value instanceof Float) {
      return DexValue.DexValueFloat.create((Float) value);
    } else if (value instanceof Double) {
      return DexValue.DexValueDouble.create((Double) value);
    } else if (value instanceof String) {
      return new DexValue.DexValueString(application.getString((String) value));

    } else if (value instanceof Type) {
      Type type = (Type) value;
      switch (type.getSort()) {
        case Type.OBJECT:
          return new DexValue.DexValueType(
              application.getTypeFromDescriptor(((Type) value).getDescriptor()));
        case Type.METHOD:
          return new DexValue.DexValueMethodType(
              application.getProto(((Type) value).getDescriptor()));
        default:
          throw new Unreachable("Type sort is not supported: " + type.getSort());
      }
    } else if (value instanceof Handle) {
      return new DexValue.DexValueMethodHandle(
          DexMethodHandle.fromAsmHandle((Handle) value, application, clazz));
    } else {
      throw new Unreachable(
          "Unsupported bootstrap static argument of type " + value.getClass().getSimpleName());
    }
  }

  private static void writeHeader(DexValueKind kind, int arg, DexOutputBuffer dest) {
    dest.putByte((byte) ((arg << 5) | kind.toByte()));
  }

  public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
    // Intentionally left empty
  }

  @Override
  protected void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    // Should never be visited.
    throw new Unreachable();
  }

  public abstract void sort();

  public abstract void writeTo(DexOutputBuffer dest, ObjectToOffsetMapping mapping);

  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object other);

  @Override
  public abstract String toString();

  public static DexValue defaultForType(DexType type) {
    switch (type.toShorty()) {
      case 'Z':
        return DexValueBoolean.DEFAULT;
      case 'B':
        return DexValueByte.DEFAULT;
      case 'C':
        return DexValueChar.DEFAULT;
      case 'S':
        return DexValueShort.DEFAULT;
      case 'I':
        return DexValueInt.DEFAULT;
      case 'J':
        return DexValueLong.DEFAULT;
      case 'F':
        return DexValueFloat.DEFAULT;
      case 'D':
        return DexValueDouble.DEFAULT;
      case 'L':
        return DexValueNull.NULL;
      default:
        throw new Unreachable("No default value for unexpected type " + type);
    }
  }

  public abstract DexType getType(DexItemFactory factory);

  public abstract Object getBoxedValue();

  /** Returns an instruction that can be used to materialize this {@link DexValue} (or null). */
  public ConstInstruction asConstInstruction(
      AppView<? extends AppInfoWithClassHierarchy> appView, IRCode code, DebugLocalInfo local) {
    return null;
  }

  public boolean isDefault(DexType type) {
    return this == defaultForType(type);
  }

  /**
   * Whether creating this value as a default value for a field might trigger an allocation.
   *
   * <p>This is conservative. It also considers allocations due to class loading when referencing a
   * field or method.
   */
  public boolean mayHaveSideEffects() {
    return true;
  }

  public abstract Object asAsmEncodedObject();

  private abstract static class SimpleDexValue extends DexValue {

    @Override
    public void sort() {
      // Intentionally empty
    }

    @Override
    public boolean mayHaveSideEffects() {
      return false;
    }

    static void writeIntegerTo(DexValueKind kind, long value, int expected, DexOutputBuffer dest) {
      // Leave space for header.
      dest.forward(1);
      int length = dest.putSignedEncodedValue(value, expected);
      dest.rewind(length + 1);
      writeHeader(kind, length - 1, dest);
      dest.forward(length);
    }
  }

  public abstract static class DexValueNumber extends SimpleDexValue {

    public abstract long getRawValue();

    @Override
    public boolean isDexValueNumber() {
      return true;
    }

    @Override
    public DexValueNumber asDexValueNumber() {
      return this;
    }

    @Override
    public AbstractValue toAbstractValue(AbstractValueFactory factory) {
      return factory.createSingleNumberValue(getRawValue());
    }
  }

  public static class DexValueByte extends DexValueNumber {

    public static final DexValueByte DEFAULT = new DexValueByte((byte) 0);

    final byte value;

    private DexValueByte(byte value) {
      this.value = value;
    }

    public static DexValueByte create(byte value) {
      return value == DEFAULT.value ? DEFAULT : new DexValueByte(value);
    }

    @Override
    int internalAcceptCompareTo(DexValue other, CompareToVisitor visitor) {
      return visitor.visitInt(value, other.asDexValueByte().value);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      visitor.visitInt(value);
    }

    public byte getValue() {
      return value;
    }

    @Override
    public DexValueKind getValueKind() {
      return DexValueKind.BYTE;
    }

    @Override
    public DexType getType(DexItemFactory factory) {
      return factory.byteType;
    }

    @Override
    public long getRawValue() {
      return value;
    }

    @Override
    public boolean isDexValueByte() {
      return true;
    }

    @Override
    public DexValueByte asDexValueByte() {
      return this;
    }

    @Override
    public Object getBoxedValue() {
      return getValue();
    }

    @Override
    public void writeTo(DexOutputBuffer dest, ObjectToOffsetMapping mapping) {
      writeHeader(DexValueKind.BYTE, 0, dest);
      dest.putSignedEncodedValue(value, 1);
    }

    @Override
    public Object asAsmEncodedObject() {
      return Integer.valueOf(value);
    }

    @Override
    public int hashCode() {
      return value * 3;
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) {
        return true;
      }
      return other instanceof DexValueByte && value == ((DexValueByte) other).value;
    }

    @Override
    public String toString() {
      return "Byte " + value;
    }

    @Override
    public ConstInstruction asConstInstruction(
        AppView<? extends AppInfoWithClassHierarchy> appView, IRCode code, DebugLocalInfo local) {
      return code.createIntConstant(value, local);
    }
  }

  public static class DexValueShort extends DexValueNumber {

    public static final DexValueShort DEFAULT = new DexValueShort((short) 0);
    final short value;

    private DexValueShort(short value) {
      this.value = value;
    }

    public static DexValueShort create(short value) {
      return value == DEFAULT.value ? DEFAULT : new DexValueShort(value);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      visitor.visitInt(value);
    }

    @Override
    int internalAcceptCompareTo(DexValue other, CompareToVisitor visitor) {
      return visitor.visitInt(value, other.asDexValueShort().getValue());
    }

    public short getValue() {
      return value;
    }

    @Override
    public DexValueKind getValueKind() {
      return DexValueKind.SHORT;
    }

    @Override
    public DexType getType(DexItemFactory factory) {
      return factory.shortType;
    }

    @Override
    public long getRawValue() {
      return value;
    }

    @Override
    public boolean isDexValueShort() {
      return true;
    }

    @Override
    public DexValueShort asDexValueShort() {
      return this;
    }

    @Override
    public Object getBoxedValue() {
      return getValue();
    }

    @Override
    public void writeTo(DexOutputBuffer dest, ObjectToOffsetMapping mapping) {
      writeIntegerTo(DexValueKind.SHORT, value, Short.BYTES, dest);
    }

    @Override
    public Object asAsmEncodedObject() {
      return Integer.valueOf(value);
    }

    @Override
    public int hashCode() {
      return value * 7;
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) {
        return true;
      }
      return other instanceof DexValueShort && value == ((DexValueShort) other).value;
    }

    @Override
    public String toString() {
      return "Short " + value;
    }

    @Override
    public ConstInstruction asConstInstruction(
        AppView<? extends AppInfoWithClassHierarchy> appView, IRCode code, DebugLocalInfo local) {
      return code.createIntConstant(value, local);
    }
  }

  public static class DexValueChar extends DexValueNumber {

    public static final DexValueChar DEFAULT = new DexValueChar((char) 0);
    final char value;

    private DexValueChar(char value) {
      this.value = value;
    }

    public static DexValueChar create(char value) {
      return value == DEFAULT.value ? DEFAULT : new DexValueChar(value);
    }

    @Override
    int internalAcceptCompareTo(DexValue other, CompareToVisitor visitor) {
      return visitor.visitInt(value, other.asDexValueChar().value);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      visitor.visitInt(value);
    }

    public char getValue() {
      return value;
    }

    @Override
    public DexValueKind getValueKind() {
      return DexValueKind.CHAR;
    }

    @Override
    public DexType getType(DexItemFactory factory) {
      return factory.charType;
    }

    @Override
    public long getRawValue() {
      return value;
    }

    @Override
    public boolean isDexValueChar() {
      return true;
    }

    @Override
    public DexValueChar asDexValueChar() {
      return this;
    }

    @Override
    public Object getBoxedValue() {
      return getValue();
    }

    @Override
    public void writeTo(DexOutputBuffer dest, ObjectToOffsetMapping mapping) {
      dest.forward(1);
      int length = dest.putUnsignedEncodedValue(value, 2);
      dest.rewind(length + 1);
      writeHeader(DexValueKind.CHAR, length - 1, dest);
      dest.forward(length);
    }

    @Override
    public Object asAsmEncodedObject() {
      return Integer.valueOf(value);
    }

    @Override
    public int hashCode() {
      return value * 5;
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) {
        return true;
      }
      return other instanceof DexValueChar && value == ((DexValueChar) other).value;
    }

    @Override
    public String toString() {
      return "Char " + value;
    }

    @Override
    public ConstInstruction asConstInstruction(
        AppView<? extends AppInfoWithClassHierarchy> appView, IRCode code, DebugLocalInfo local) {
      return code.createIntConstant(value, local);
    }
  }

  public static class DexValueInt extends DexValueNumber {

    public static final DexValueInt DEFAULT = new DexValueInt(0);
    public final int value;

    private DexValueInt(int value) {
      this.value = value;
    }

    public static DexValueInt create(int value) {
      return value == DEFAULT.value ? DEFAULT : new DexValueInt(value);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      visitor.visitInt(value);
    }

    @Override
    int internalAcceptCompareTo(DexValue other, CompareToVisitor visitor) {
      return visitor.visitInt(value, other.asDexValueInt().value);
    }

    public int getValue() {
      return value;
    }

    @Override
    public DexValueKind getValueKind() {
      return DexValueKind.INT;
    }

    @Override
    public DexType getType(DexItemFactory factory) {
      return factory.intType;
    }

    @Override
    public long getRawValue() {
      return value;
    }

    @Override
    public Object getBoxedValue() {
      return getValue();
    }

    @Override
    public void writeTo(DexOutputBuffer dest, ObjectToOffsetMapping mapping) {
      writeIntegerTo(DexValueKind.INT, value, Integer.BYTES, dest);
    }

    @Override
    public boolean isDexValueInt() {
      return true;
    }

    @Override
    public DexValueInt asDexValueInt() {
      return this;
    }

    @Override
    public Object asAsmEncodedObject() {
      return Integer.valueOf(value);
    }

    @Override
    public int hashCode() {
      return value * 11;
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) {
        return true;
      }
      return other instanceof DexValueInt && value == ((DexValueInt) other).value;
    }

    @Override
    public String toString() {
      return "Int " + value;
    }

    @Override
    public ConstInstruction asConstInstruction(
        AppView<? extends AppInfoWithClassHierarchy> appView, IRCode code, DebugLocalInfo local) {
      return code.createIntConstant(value, local);
    }
  }

  public static class DexValueLong extends DexValueNumber {

    public static final DexValueLong DEFAULT = new DexValueLong(0);
    final long value;

    private DexValueLong(long value) {
      this.value = value;
    }

    public static DexValueLong create(long value) {
      return value == DEFAULT.value ? DEFAULT : new DexValueLong(value);
    }

    @Override
    int internalAcceptCompareTo(DexValue other, CompareToVisitor visitor) {
      return visitor.visitLong(value, other.asDexValueLong().value);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      visitor.visitLong(value);
    }

    public long getValue() {
      return value;
    }

    @Override
    public DexValueKind getValueKind() {
      return DexValueKind.LONG;
    }

    @Override
    public DexType getType(DexItemFactory factory) {
      return factory.longType;
    }

    @Override
    public long getRawValue() {
      return value;
    }

    @Override
    public boolean isDexValueLong() {
      return true;
    }

    @Override
    public DexValueLong asDexValueLong() {
      return this;
    }

    @Override
    public Object getBoxedValue() {
      return getValue();
    }

    @Override
    public void writeTo(DexOutputBuffer dest, ObjectToOffsetMapping mapping) {
      writeIntegerTo(DexValueKind.LONG, value, Long.BYTES, dest);
    }

    @Override
    public Object asAsmEncodedObject() {
      return Long.valueOf(value);
    }

    @Override
    public int hashCode() {
      return (int) value * 13;
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) {
        return true;
      }
      return other instanceof DexValueLong && value == ((DexValueLong) other).value;
    }

    @Override
    public String toString() {
      return "Long " + value;
    }

    @Override
    public ConstInstruction asConstInstruction(
        AppView<? extends AppInfoWithClassHierarchy> appView, IRCode code, DebugLocalInfo local) {
      return code.createLongConstant(value, local);
    }
  }

  public static class DexValueFloat extends DexValueNumber {

    public static final DexValueFloat DEFAULT = new DexValueFloat(0);
    final float value;

    private DexValueFloat(float value) {
      this.value = value;
    }

    public static DexValueFloat create(float value) {
      return Float.compare(value, DEFAULT.value) == 0 ? DEFAULT : new DexValueFloat(value);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      visitor.visitFloat(value);
    }

    @Override
    int internalAcceptCompareTo(DexValue other, CompareToVisitor visitor) {
      return visitor.visitFloat(value, other.asDexValueFloat().value);
    }

    public float getValue() {
      return value;
    }

    @Override
    public DexValueKind getValueKind() {
      return DexValueKind.FLOAT;
    }

    @Override
    public DexType getType(DexItemFactory factory) {
      return factory.floatType;
    }

    @Override
    public long getRawValue() {
      return Float.floatToIntBits(value);
    }

    @Override
    public boolean isDexValueFloat() {
      return true;
    }

    @Override
    public DexValueFloat asDexValueFloat() {
      return this;
    }

    @Override
    public Object getBoxedValue() {
      return getValue();
    }

    @Override
    public void writeTo(DexOutputBuffer dest, ObjectToOffsetMapping mapping) {
      dest.forward(1);
      int length = EncodedValueUtils.putFloat(dest, value);
      dest.rewind(length + 1);
      writeHeader(DexValueKind.FLOAT, length - 1, dest);
      dest.forward(length);
    }

    @Override
    public Object asAsmEncodedObject() {
      return Float.valueOf(value);
    }

    @Override
    public ConstInstruction asConstInstruction(
        AppView<? extends AppInfoWithClassHierarchy> appView, IRCode code, DebugLocalInfo local) {
      return code.createFloatConstant(value, local);
    }

    @Override
    public int hashCode() {
      return (int) (value * 19);
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) {
        return true;
      }
      return other instanceof DexValueFloat
          && Float.compare(value, ((DexValueFloat) other).value) == 0;
    }

    @Override
    public String toString() {
      return "Float " + value;
    }
  }

  public static class DexValueDouble extends DexValueNumber {

    public static final DexValueDouble DEFAULT = new DexValueDouble(0);

    final double value;

    private DexValueDouble(double value) {
      this.value = value;
    }

    public static DexValueDouble create(double value) {
      return Double.compare(value, DEFAULT.value) == 0 ? DEFAULT : new DexValueDouble(value);
    }

    @Override
    int internalAcceptCompareTo(DexValue other, CompareToVisitor visitor) {
      return visitor.visitDouble(value, other.asDexValueDouble().value);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      visitor.visitDouble(value);
    }

    public double getValue() {
      return value;
    }

    @Override
    public DexValueKind getValueKind() {
      return DexValueKind.DOUBLE;
    }

    @Override
    public DexType getType(DexItemFactory factory) {
      return factory.doubleType;
    }

    @Override
    public long getRawValue() {
      return Double.doubleToRawLongBits(value);
    }

    @Override
    public boolean isDexValueDouble() {
      return true;
    }

    @Override
    public DexValueDouble asDexValueDouble() {
      return this;
    }

    @Override
    public Object getBoxedValue() {
      return getValue();
    }

    @Override
    public void writeTo(DexOutputBuffer dest, ObjectToOffsetMapping mapping) {
      dest.forward(1);
      int length = EncodedValueUtils.putDouble(dest, value);
      dest.rewind(length + 1);
      writeHeader(DexValueKind.DOUBLE, length - 1, dest);
      dest.forward(length);
    }

    @Override
    public Object asAsmEncodedObject() {
      return Double.valueOf(value);
    }

    @Override
    public ConstInstruction asConstInstruction(
        AppView<? extends AppInfoWithClassHierarchy> appView, IRCode code, DebugLocalInfo local) {
      return code.createDoubleConstant(value, local);
    }

    @Override
    public int hashCode() {
      return (int) (value * 29);
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) {
        return true;
      }
      return (other instanceof DexValueDouble) &&
          (Double.compare(value, ((DexValueDouble) other).value) == 0);
    }

    @Override
    public String toString() {
      return "Double " + value;
    }
  }

  static private abstract class NestedDexValue<T extends IndexedDexItem> extends DexValue {

    public final T value;

    private NestedDexValue(T value) {
      this.value = value;
    }

    @Override
    public boolean isNestedDexValue() {
      return true;
    }

    @Override
    public DexType getType(DexItemFactory factory) {
      throw new Unreachable();
    }

    public T getValue() {
      return value;
    }

    @Override
    public void writeTo(DexOutputBuffer dest, ObjectToOffsetMapping mapping) {
      int offset = value.getOffset(mapping);
      dest.forward(1);
      int length = dest.putUnsignedEncodedValue(offset, 4);
      dest.rewind(length + 1);
      writeHeader(getValueKind(), length - 1, dest);
      dest.forward(length);
    }

    @Override
    public Object getBoxedValue() {
      throw new Unreachable("No boxed value for DexValue " + this.getClass().getSimpleName());
    }

    @Override
    public Object asAsmEncodedObject() {
      throw new Unreachable("No ASM conversion for DexValue " + this.getClass().getSimpleName());
    }

    @Override
    public void sort() {
      // Intentionally empty.
    }

    @Override
    public int hashCode() {
      return value.hashCode() * 7 + getValueKind().toByte();
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) {
        return true;
      }
      if (other instanceof NestedDexValue) {
        NestedDexValue<?> that = (NestedDexValue<?>) other;
        return that.getValueKind() == getValueKind() && that.value.equals(value);
      }
      return false;
    }

    @Override
    public String toString() {
      return "Item " + getValueKind() + " " + value;
    }
  }

  static public class DexValueString extends NestedDexValue<DexString> {

    public DexValueString(DexString value) {
      super(value);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      value.acceptHashing(visitor);
    }

    @Override
    int internalAcceptCompareTo(DexValue other, CompareToVisitor visitor) {
      int order = DexItemBasedValueString.compareAndCheckValueStrings(this, other, visitor);
      if (order != 0) {
        return order;
      }
      return value.acceptCompareTo(other.asDexValueString().value, visitor);
    }

    @Override
    public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
      value.collectIndexedItems(indexedItems);
    }

    @Override
    public DexValueString asDexValueString() {
      return this;
    }

    @Override
    public boolean isDexValueString() {
      return true;
    }

    @Override
    public Object asAsmEncodedObject() {
      return value.toString();
    }

    @Override
    public DexValueKind getValueKind() {
      return DexValueKind.STRING;
    }

    @Override
    public DexType getType(DexItemFactory factory) {
      return factory.stringType;
    }

    @Override
    public ConstInstruction asConstInstruction(
        AppView<? extends AppInfoWithClassHierarchy> appView, IRCode code, DebugLocalInfo local) {
      TypeElement type = TypeElement.stringClassType(appView, definitelyNotNull());
      Value outValue = code.createValue(type, local);
      ConstString instruction = new ConstString(outValue, value);
      if (!instruction.instructionInstanceCanThrow(appView, code.context())) {
        return instruction;
      }
      return null;
    }

    @Override
    public boolean mayHaveSideEffects() {
      // Assuming that strings do not have side-effects.
      return false;
    }

    @Override
    public AbstractValue toAbstractValue(AbstractValueFactory factory) {
      return factory.createSingleStringValue(value);
    }
  }

  public static class DexItemBasedValueString extends NestedDexValue<DexReference> {

    // Helper to ensure a consistent order on DexValueString and DexItemBasedValueString which are
    // both defined to have kind 'string'.
    static int compareAndCheckValueStrings(DexValue v1, DexValue v2, CompareToVisitor visitor) {
      assert v1.getValueKind() == DexValueKind.STRING;
      assert v2.getValueKind() == DexValueKind.STRING;
      int order1 = v1.isDexItemBasedValueString() ? 1 : 0;
      int order2 = v2.isDexItemBasedValueString() ? 1 : 0;
      return visitor.visitInt(order1, order2);
    }

    private final NameComputationInfo<?> nameComputationInfo;

    public DexItemBasedValueString(DexReference value, NameComputationInfo<?> nameComputationInfo) {
      super(value);
      this.nameComputationInfo = nameComputationInfo;
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      visitor.visitDexReference(value);
    }

    @Override
    int internalAcceptCompareTo(DexValue other, CompareToVisitor visitor) {
      int order = compareAndCheckValueStrings(this, other, visitor);
      if (order != 0) {
        return order;
      }
      return visitor.visitDexReference(value, other.asDexItemBasedValueString().value);
    }

    @Override
    public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
      value.collectIndexedItems(appView, indexedItems);
    }

    public NameComputationInfo<?> getNameComputationInfo() {
      return nameComputationInfo;
    }

    @Override
    public boolean isDexItemBasedValueString() {
      return true;
    }

    @Override
    public DexItemBasedValueString asDexItemBasedValueString() {
      return this;
    }

    @Override
    public Object asAsmEncodedObject() {
      return value.toString();
    }

    @Override
    public DexValueKind getValueKind() {
      return DexValueKind.STRING;
    }

    @Override
    public DexType getType(DexItemFactory factory) {
      return factory.stringType;
    }

    @Override
    public ConstInstruction asConstInstruction(
        AppView<? extends AppInfoWithClassHierarchy> appView, IRCode code, DebugLocalInfo local) {
      TypeElement type = TypeElement.stringClassType(appView, definitelyNotNull());
      Value outValue = code.createValue(type, local);
      DexItemBasedConstString instruction =
          new DexItemBasedConstString(outValue, value, nameComputationInfo);
      // DexItemBasedConstString cannot throw.
      assert !instruction.instructionInstanceCanThrow(appView, code.context());
      return instruction;
    }

    @Override
    public AbstractValue toAbstractValue(AbstractValueFactory factory) {
      return factory.createSingleDexItemBasedStringValue(value, nameComputationInfo);
    }

    @Override
    public void writeTo(DexOutputBuffer dest, ObjectToOffsetMapping mapping) {
      throw new Unreachable(
          "DexItemBasedValueString values should always be rewritten into DexValueString");
    }
  }

  static public class DexValueType extends NestedDexValue<DexType> {

    public DexValueType(DexType value) {
      super(value);
    }

    @Override
    int internalAcceptCompareTo(DexValue other, CompareToVisitor visitor) {
      return value.acceptCompareTo(other.asDexValueType().value, visitor);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      value.acceptHashing(visitor);
    }

    @Override
    public DexValueKind getValueKind() {
      return DexValueKind.TYPE;
    }

    @Override
    public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
      value.collectIndexedItems(appView, indexedItems);
    }

    @Override
    public DexValueType asDexValueType() {
      return this;
    }

    @Override
    public boolean isDexValueType() {
      return true;
    }

    @Override
    public AbstractValue toAbstractValue(AbstractValueFactory factory) {
      return UnknownValue.getInstance();
    }
  }

  static public class DexValueField extends NestedDexValue<DexField> {

    public DexValueField(DexField value) {
      super(value);
    }

    @Override
    int internalAcceptCompareTo(DexValue other, CompareToVisitor visitor) {
      return value.acceptCompareTo(other.asDexValueField().value, visitor);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      value.acceptHashing(visitor);
    }

    @Override
    public DexValueKind getValueKind() {
      return DexValueKind.FIELD;
    }

    @Override
    public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
      value.collectIndexedItems(appView, indexedItems);
    }

    @Override
    public boolean isDexValueField() {
      return true;
    }

    @Override
    public DexValueField asDexValueField() {
      return this;
    }

    @Override
    public AbstractValue toAbstractValue(AbstractValueFactory factory) {
      return UnknownValue.getInstance();
    }
  }

  static public class DexValueMethod extends NestedDexValue<DexMethod> {

    public DexValueMethod(DexMethod value) {
      super(value);
    }

    @Override
    int internalAcceptCompareTo(DexValue other, CompareToVisitor visitor) {
      return value.acceptCompareTo(other.asDexValueMethod().value, visitor);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      value.acceptHashing(visitor);
    }

    @Override
    public DexValueKind getValueKind() {
      return DexValueKind.METHOD;
    }

    @Override
    public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
      value.collectIndexedItems(appView, indexedItems);
    }

    @Override
    public boolean isDexValueMethod() {
      return true;
    }

    @Override
    public DexValueMethod asDexValueMethod() {
      return this;
    }

    @Override
    public AbstractValue toAbstractValue(AbstractValueFactory factory) {
      return UnknownValue.getInstance();
    }
  }

  static public class DexValueEnum extends NestedDexValue<DexField> {

    public DexValueEnum(DexField value) {
      super(value);
    }

    @Override
    int internalAcceptCompareTo(DexValue other, CompareToVisitor visitor) {
      return value.acceptCompareTo(other.asDexValueEnum().value, visitor);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      value.acceptHashing(visitor);
    }

    @Override
    public DexValueKind getValueKind() {
      return DexValueKind.ENUM;
    }

    @Override
    public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
      value.collectIndexedItems(appView, indexedItems);
    }

    @Override
    public boolean isDexValueEnum() {
      return true;
    }

    @Override
    public DexValueEnum asDexValueEnum() {
      return this;
    }

    @Override
    public AbstractValue toAbstractValue(AbstractValueFactory factory) {
      return UnknownValue.getInstance();
    }
  }

  static public class DexValueMethodType extends NestedDexValue<DexProto> {

    public DexValueMethodType(DexProto value) {
      super(value);
    }

    @Override
    int internalAcceptCompareTo(DexValue other, CompareToVisitor visitor) {
      return value.acceptCompareTo(other.asDexValueMethodType().value, visitor);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      value.acceptHashing(visitor);
    }

    @Override
    public boolean isDexValueMethodType() {
      return true;
    }

    @Override
    public DexValueMethodType asDexValueMethodType() {
      return this;
    }

    @Override
    public DexValueKind getValueKind() {
      return DexValueKind.METHOD_TYPE;
    }

    @Override
    public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
      value.collectIndexedItems(appView, indexedItems);
    }

    @Override
    public AbstractValue toAbstractValue(AbstractValueFactory factory) {
      return UnknownValue.getInstance();
    }
  }

  static public class DexValueArray extends DexValue {

    final DexValue[] values;

    public DexValueArray(DexValue[] values) {
      this.values = values;
    }

    @Override
    int internalAcceptCompareTo(DexValue other, CompareToVisitor visitor) {
      return visitor.visitItemArray(values, other.asDexValueArray().values);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      visitor.visitItemArray(values);
    }

    public void forEachElement(Consumer<DexValue> consumer) {
      for (DexValue value : values) {
        consumer.accept(value);
      }
    }

    public DexValue[] getValues() {
      return values;
    }

    public int size() {
      return values.length;
    }

    public DexValue getValue(int i) {
      return values[i];
    }

    @Override
    public DexValueKind getValueKind() {
      return DexValueKind.ARRAY;
    }

    @Override
    public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
      for (DexValue value : values) {
        value.collectIndexedItems(appView, indexedItems);
      }
    }

    @Override
    public void writeTo(DexOutputBuffer dest, ObjectToOffsetMapping mapping) {
      writeHeader(DexValueKind.ARRAY, 0, dest);
      dest.putUleb128(values.length);
      for (DexValue value : values) {
        value.writeTo(dest, mapping);
      }
    }

    @Override
    public DexType getType(DexItemFactory factory) {
      throw new Unreachable();
    }

    @Override
    public Object getBoxedValue() {
      throw new Unreachable("No boxed value for DexValueArray");
    }

    @Override
    public AbstractValue toAbstractValue(AbstractValueFactory factory) {
      return UnknownValue.getInstance();
    }

    @Override
    public Object asAsmEncodedObject() {
      throw new Unreachable("No ASM conversion for DexValueArray");
    }

    @Override
    public void sort() {
      for (DexValue value : values) {
        value.sort();
      }
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(values);
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) {
        return true;
      }
      if (other instanceof DexValueArray) {
        DexValueArray that = (DexValueArray) other;
        return Arrays.equals(that.values, values);
      }
      return false;
    }

    @Override
    public String toString() {
      return "Array " + Arrays.toString(values);
    }

    @Override
    public boolean isDexValueArray() {
      return true;
    }

    @Override
    public DexValueArray asDexValueArray() {
      return this;
    }
  }

  static public class DexValueAnnotation extends DexValue {

    public final DexEncodedAnnotation value;

    public DexValueAnnotation(DexEncodedAnnotation value) {
      this.value = value;
    }

    @Override
    int internalAcceptCompareTo(DexValue other, CompareToVisitor visitor) {
      return value.acceptCompareTo(other.asDexValueAnnotation().value, visitor);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      value.acceptHashing(visitor);
    }

    public DexEncodedAnnotation getValue() {
      return value;
    }

    @Override
    public DexValueKind getValueKind() {
      return DexValueKind.ANNOTATION;
    }

    @Override
    public boolean isDexValueAnnotation() {
      return true;
    }

    @Override
    public DexValueAnnotation asDexValueAnnotation() {
      return this;
    }

    @Override
    public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
      value.collectIndexedItems(appView, indexedItems);
    }

    @Override
    public AbstractValue toAbstractValue(AbstractValueFactory factory) {
      return UnknownValue.getInstance();
    }

    @Override
    public void writeTo(DexOutputBuffer dest, ObjectToOffsetMapping mapping) {
      writeHeader(DexValueKind.ANNOTATION, 0, dest);
      FileWriter.writeEncodedAnnotation(value, dest, mapping);
    }

    @Override
    public DexType getType(DexItemFactory factory) {
      throw new Unreachable();
    }

    @Override
    public Object getBoxedValue() {
      throw new Unreachable("No boxed value for DexValueAnnotation");
    }

    @Override
    public Object asAsmEncodedObject() {
      throw new Unreachable("No ASM conversion for DexValueAnnotation");
    }

    @Override
    public void sort() {
      value.sort();
    }

    @Override
    public int hashCode() {
      return value.hashCode() * 7;
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) {
        return true;
      }
      if (other instanceof DexValueAnnotation) {
        DexValueAnnotation that = (DexValueAnnotation) other;
        return that.value.equals(value);
      }
      return false;
    }

    @Override
    public String toString() {
      return "Annotation " + value;
    }
  }

  public static class DexValueNull extends DexValueNumber {

    public static final DexValue NULL = new DexValueNull();

    // See DexValueNull.NULL
    private DexValueNull() {
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      assert this == NULL;
    }

    @Override
    int internalAcceptCompareTo(DexValue other, CompareToVisitor visitor) {
      assert this == NULL;
      assert other == NULL;
      return 0;
    }

    public Object getValue() {
      return null;
    }

    @Override
    public DexValueKind getValueKind() {
      return DexValueKind.NULL;
    }

    @Override
    public DexType getType(DexItemFactory factory) {
      throw new Unreachable();
    }

    @Override
    public long getRawValue() {
      return 0;
    }

    @Override
    public void writeTo(DexOutputBuffer dest, ObjectToOffsetMapping mapping) {
      writeHeader(DexValueKind.NULL, 0, dest);
    }

    @Override
    public boolean isDexValueNull() {
      return true;
    }

    @Override
    public DexValueNull asDexValueNull() {
      return this;
    }

    @Override
    public Object getBoxedValue() {
      return null;
    }

    @Override
    public Object asAsmEncodedObject() {
      return null;
    }

    @Override
    public int hashCode() {
      return 42;
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) {
        return true;
      }
      return other instanceof DexValueNull;
    }

    @Override
    public String toString() {
      return "Null";
    }

    @Override
    public ConstInstruction asConstInstruction(
        AppView<? extends AppInfoWithClassHierarchy> appView, IRCode code, DebugLocalInfo local) {
      return code.createConstNull(local);
    }
  }

  public static class DexValueBoolean extends DexValueNumber {

    private static final DexValueBoolean TRUE = new DexValueBoolean(true);
    private static final DexValueBoolean FALSE = new DexValueBoolean(false);
    // Use a separate instance for the default value to distinguish it from an explicit false value.
    private static final DexValueBoolean DEFAULT = new DexValueBoolean(false);

    final boolean value;

    private DexValueBoolean(boolean value) {
      this.value = value;
    }

    public static DexValueBoolean create(boolean value) {
      return value ? TRUE : FALSE;
    }

    @Override
    int internalAcceptCompareTo(DexValue other, CompareToVisitor visitor) {
      return visitor.visitBool(value, other.asDexValueBoolean().value);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      visitor.visitBool(value);
    }

    public boolean getValue() {
      return value;
    }

    @Override
    public DexValueKind getValueKind() {
      return DexValueKind.BOOLEAN;
    }

    @Override
    public DexType getType(DexItemFactory factory) {
      return factory.booleanType;
    }

    @Override
    public long getRawValue() {
      return BooleanUtils.longValue(value);
    }

    @Override
    public boolean isDexValueBoolean() {
      return true;
    }

    @Override
    public DexValueBoolean asDexValueBoolean() {
      return this;
    }

    @Override
    public Object getBoxedValue() {
      return getValue();
    }

    @Override
    public void writeTo(DexOutputBuffer dest, ObjectToOffsetMapping mapping) {
      writeHeader(DexValueKind.BOOLEAN, value ? 1 : 0, dest);
    }

    @Override
    public Object asAsmEncodedObject() {
      return Integer.valueOf(value ? 1 : 0);
    }

    @Override
    public int hashCode() {
      return value ? 1234 : 4321;
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) {
        return true;
      }
      return other instanceof DexValueBoolean && ((DexValueBoolean) other).value == value;
    }

    @Override
    public String toString() {
      return value ? "True" : "False";
    }

    @Override
    public ConstInstruction asConstInstruction(
        AppView<? extends AppInfoWithClassHierarchy> appView, IRCode code, DebugLocalInfo local) {
      return code.createIntConstant(BooleanUtils.intValue(value), local);
    }
  }

  static public class DexValueMethodHandle extends NestedDexValue<DexMethodHandle> {

    public DexValueMethodHandle(DexMethodHandle value) {
      super(value);
    }

    @Override
    int internalAcceptCompareTo(DexValue other, CompareToVisitor visitor) {
      return value.acceptCompareTo(other.asDexValueMethodHandle().value, visitor);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      value.acceptHashing(visitor);
    }

    @Override
    public boolean isDexValueMethodHandle() {
      return true;
    }

    @Override
    public DexValueMethodHandle asDexValueMethodHandle() {
      return this;
    }

    @Override
    public DexValueKind getValueKind() {
      return DexValueKind.METHOD_HANDLE;
    }

    @Override
    public void collectIndexedItems(AppView<?> appView, IndexedItemCollection indexedItems) {
      value.collectIndexedItems(appView, indexedItems);
    }

    @Override
    public AbstractValue toAbstractValue(AbstractValueFactory factory) {
      return UnknownValue.getInstance();
    }
  }
}
