// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.analysis.proto.schema.ProtoFieldInfo;
import com.android.tools.r8.ir.analysis.proto.schema.ProtoFieldType;
import com.android.tools.r8.ir.analysis.proto.schema.ProtoFieldTypeFactory;
import com.android.tools.r8.ir.analysis.proto.schema.ProtoMessageInfo;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.ThrowingCharIterator;
import com.android.tools.r8.utils.ThrowingIntIterator;
import com.android.tools.r8.utils.ThrowingIterator;
import java.io.UTFDataFormatException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.OptionalInt;

/**
 * The generated class for a protobuf message will have a dynamicMethod(), where the schema of the
 * protobuf message is encoded:
 *
 * <pre>
 *   class SomeMessage {
 *     ...
 *     Object dynamicMethod(MethodToInvoke method) {
 *       switch (method) {
 *         ...
 *         case BUILD_MESSAGE_INFO:
 *           Object[] objects = new Object[] { ... };
 *           String info = "...";
 *           return newMessageInfo(DEFAULT_INSTANCE, info, objects);
 *         ...
 *       }
 *     }
 *   }
 * </pre>
 *
 * This class can be used to decode the encoded schema, given the values `objects` and `info`.
 */
public class RawMessageInfoDecoder {

  private static final int IS_PROTO_2_MASK = 0x1;

  private final ProtoFieldTypeFactory factory;

  RawMessageInfoDecoder(ProtoFieldTypeFactory factory) {
    this.factory = factory;
  }

  public ProtoMessageInfo run(Value infoValue, Value objectsValue) {
    try {
      ProtoMessageInfo.Builder builder = ProtoMessageInfo.builder();
      ThrowingIntIterator<InvalidRawMessageInfoException> infoIterator =
          createInfoIterator(infoValue);

      // flags := info[0].
      int flags = infoIterator.nextIntComputeIfAbsent(this::invalidInfoFailure);
      builder.setFlags(flags);

      // fieldCount := info[1].
      int fieldCount = infoIterator.nextIntComputeIfAbsent(this::invalidInfoFailure);
      if (fieldCount == 0) {
        return builder.build();
      }

      // numberOfOneOfObjects := info[2].
      int numberOfOneOfObjects = infoIterator.nextIntComputeIfAbsent(this::invalidInfoFailure);

      // numberOfHasBitsObjects := info[3].
      int numberOfHasBitsObjects = infoIterator.nextIntComputeIfAbsent(this::invalidInfoFailure);

      // minFieldNumber     := info[4].
      // maxFieldNumber     := info[5].
      // entryCount         := info[6].
      // mapFieldCount      := info[7].
      // repeatedFieldCount := info[8].
      // checkInitialized   := info[9].
      for (int i = 4; i < 10; i++) {
        // No need to store these values, since they can be computed from the rest (and need to be
        // recomputed if the proto is changed).
        infoIterator.nextIntComputeIfAbsent(this::invalidInfoFailure);
      }

      ThrowingIterator<Value, InvalidRawMessageInfoException> objectIterator =
          createObjectIterator(objectsValue);

      if (numberOfOneOfObjects > 0) {
        builder.setNumberOfOneOfObjects(numberOfOneOfObjects);
        for (int i = 0; i < numberOfOneOfObjects; i++) {
          builder.addOneOfObject(
              objectIterator.computeNextIfAbsent(this::invalidObjectsFailure),
              objectIterator.computeNextIfAbsent(this::invalidObjectsFailure));
        }
      }

      if (numberOfHasBitsObjects > 0) {
        builder.setNumberOfHasBitsObjects(numberOfHasBitsObjects);
        for (int i = 0; i < numberOfHasBitsObjects; i++) {
          builder.addHasBitsObject(objectIterator.computeNextIfAbsent(this::invalidObjectsFailure));
        }
      }

      boolean isProto2 = (flags & IS_PROTO_2_MASK) != 0;
      for (int i = 0; i < fieldCount; i++) {
        // Extract field-specific portion of "info" string.
        int fieldNumber = infoIterator.nextIntComputeIfAbsent(this::invalidInfoFailure);
        int fieldTypeWithExtraBits = infoIterator.nextIntComputeIfAbsent(this::invalidInfoFailure);
        ProtoFieldType fieldType = factory.createField(fieldTypeWithExtraBits);

        OptionalInt auxData;
        if (fieldType.hasAuxData(isProto2)) {
          auxData = OptionalInt.of(infoIterator.nextIntComputeIfAbsent(this::invalidInfoFailure));
        } else {
          auxData = OptionalInt.empty();
        }

        // Extract field-specific portion of "objects" array.
        int numberOfObjects = fieldType.numberOfObjects(isProto2, factory);
        try {
          List<Value> fieldObjects = objectIterator.take(numberOfObjects);
          builder.addField(new ProtoFieldInfo(fieldNumber, fieldType, auxData, fieldObjects));
        } catch (NoSuchElementException e) {
          throw new InvalidRawMessageInfoException();
        }
      }

      // Verify that the input was fully consumed.
      if (infoIterator.hasNext() || objectIterator.hasNext()) {
        throw new InvalidRawMessageInfoException();
      }

      return builder.build();
    } catch (InvalidRawMessageInfoException e) {
      // This should generally not happen, so leave an assert here just in case.
      assert false;
      return null;
    }
  }

  private int invalidInfoFailure() throws InvalidRawMessageInfoException {
    throw new InvalidRawMessageInfoException();
  }

  private Value invalidObjectsFailure() throws InvalidRawMessageInfoException {
    throw new InvalidRawMessageInfoException();
  }

  private static ThrowingIntIterator<InvalidRawMessageInfoException> createInfoIterator(
      Value infoValue) throws InvalidRawMessageInfoException {
    if (!infoValue.isPhi() && infoValue.definition.isConstString()) {
      return createInfoIterator(infoValue.definition.asConstString().getValue());
    }
    throw new InvalidRawMessageInfoException();
  }

  /** Returns an iterator that yields the integers that results from decoding the given string. */
  private static ThrowingIntIterator<InvalidRawMessageInfoException> createInfoIterator(
      DexString info) {
    return new ThrowingIntIterator<InvalidRawMessageInfoException>() {

      private final ThrowingCharIterator<UTFDataFormatException> charIterator = info.iterator();

      @Override
      public boolean hasNext() {
        return charIterator.hasNext();
      }

      @Override
      public int nextInt() throws InvalidRawMessageInfoException {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        int value = 0;
        int shift = 0;
        while (true) {
          char c;
          try {
            c = charIterator.nextChar();
          } catch (UTFDataFormatException e) {
            throw new InvalidRawMessageInfoException();
          }
          if (c >= 0xD800 && c < 0xE000) {
            throw new InvalidRawMessageInfoException();
          }
          if (c < 0xD800) {
            return value | (c << shift);
          }
          value |= (c & 0x1FFF) << shift;
          shift += 13;
          if (!hasNext()) {
            throw new InvalidRawMessageInfoException();
          }
        }
      }
    };
  }

  /**
   * Returns an iterator that yields the values that are stored in the `objects` array that is
   * passed to GeneratedMessageLite.newMessageInfo(). The array values are returned in-order, i.e.,
   * the value objects[i] will be returned prior to the value objects[i+1].
   */
  private static ThrowingIterator<Value, InvalidRawMessageInfoException> createObjectIterator(
      Value objectsValue) throws InvalidRawMessageInfoException {
    if (objectsValue.isPhi() || !objectsValue.definition.isNewArrayEmpty()) {
      throw new InvalidRawMessageInfoException();
    }

    NewArrayEmpty newArrayEmpty = objectsValue.definition.asNewArrayEmpty();
    int expectedArraySize = objectsValue.uniqueUsers().size() - 1;

    // Verify that the size is correct.
    Value sizeValue = newArrayEmpty.size().getAliasedValue();
    if (sizeValue.isPhi()
        || !sizeValue.definition.isConstNumber()
        || sizeValue.definition.asConstNumber().getIntValue() != expectedArraySize) {
      throw new InvalidRawMessageInfoException();
    }

    // Create an iterator for the block of interest.
    InstructionListIterator instructionIterator = newArrayEmpty.getBlock().listIterator();
    instructionIterator.nextUntil(instruction -> instruction == newArrayEmpty);

    return new ThrowingIterator<Value, InvalidRawMessageInfoException>() {

      private int expectedNextIndex = 0;

      @Override
      public boolean hasNext() {
        while (instructionIterator.hasNext()) {
          Instruction next = instructionIterator.peekNext();
          if (isArrayPutOfInterest(next)) {
            return true;
          }
          if (next.isJumpInstruction()) {
            return false;
          }
          instructionIterator.next();
        }
        return false;
      }

      @Override
      public Value next() throws InvalidRawMessageInfoException {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        ArrayPut arrayPut = instructionIterator.next().asArrayPut();

        // Verify that the index correct.
        Value indexValue = arrayPut.index().getAliasedValue();
        if (indexValue.isPhi()
            || !indexValue.definition.isConstNumber()
            || indexValue.definition.asConstNumber().getIntValue() != expectedNextIndex) {
          throw new InvalidRawMessageInfoException();
        }

        expectedNextIndex++;
        return arrayPut.value().getAliasedValue();
      }

      private boolean isArrayPutOfInterest(Instruction instruction) {
        return instruction.isArrayPut()
            && instruction.asArrayPut().array().getAliasedValue() == objectsValue;
      }
    };
  }

  private static class InvalidRawMessageInfoException extends Exception {}
}
