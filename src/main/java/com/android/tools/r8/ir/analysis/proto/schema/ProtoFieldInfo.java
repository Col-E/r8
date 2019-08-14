// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto.schema;

import static com.android.tools.r8.ir.analysis.proto.schema.ProtoMessageInfo.BITS_PER_HAS_BITS_WORD;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import java.util.List;
import java.util.OptionalInt;

public class ProtoFieldInfo {

  private final int number;
  private final ProtoFieldType type;

  private final OptionalInt auxData;
  private final List<ProtoObject> objects;

  public ProtoFieldInfo(
      int number, ProtoFieldType type, OptionalInt auxData, List<ProtoObject> objects) {
    this.number = number;
    this.type = type;
    this.auxData = auxData;
    this.objects = objects;
  }

  public boolean hasAuxData() {
    return auxData.isPresent();
  }

  public int getAuxData() {
    assert hasAuxData();
    return auxData.getAsInt();
  }

  public int getNumber() {
    return number;
  }

  public List<ProtoObject> getObjects() {
    return objects;
  }

  public ProtoFieldType getType() {
    return type;
  }

  /**
   * For singular/repeated message-type fields, the type of the message.
   *
   * <p>This isn't populated for map-type fields because that's a bit difficult, but this doesn't
   * matter in practice. We only use this for determining whether to retain a field based on whether
   * it reaches a map/required field, but there's no need to go through that when we're already at
   * one.
   */
  public DexType getBaseMessageType(ProtoFieldTypeFactory factory) {
    if (type.isOneOf()) {
      ProtoFieldType actualFieldType = type.asOneOf().getActualFieldType(factory);
      if (actualFieldType.isGroup() || actualFieldType.isMessage()) {
        ProtoObject object = objects.get(0);
        assert object.isProtoTypeObject();
        return object.asProtoTypeObject().getType();
      }
      return null;
    }
    if (type.isMessage() || type.isGroup()) {
      ProtoObject object = objects.get(0);
      assert object.isProtoFieldObject() : object.toString();
      return object.asProtoFieldObject().getField().type;
    }
    if (type.isMessageList() || type.isGroupList()) {
      ProtoObject object = objects.get(1);
      assert object.isProtoTypeObject();
      return object.asProtoTypeObject().getType();
    }
    return null;
  }

  /**
   * (Proto2 singular fields only.)
   *
   * <p>Java field for denoting the presence of a protobuf field.
   *
   * <p>The generated Java code for:
   *
   * <pre>
   *   message MyMessage {
   *     optional int32 foo = 123;
   *     optional int32 bar = 456;
   *   }
   * </pre>
   *
   * looks like:
   *
   * <pre>
   *   boolean hasFoo() { return bitField0_ & 0x1; }
   *   boolean hasBar() { return bitField0_ & 0x2; }
   * </pre>
   */
  public boolean hasHazzerBitField(ProtoMessageInfo protoMessageInfo) {
    return protoMessageInfo.isProto2() && type.isSingular();
  }

  public DexField getHazzerBitField(ProtoMessageInfo protoMessageInfo) {
    assert hasHazzerBitField(protoMessageInfo);

    int hasBitsIndex = getAuxData() / BITS_PER_HAS_BITS_WORD;
    assert hasBitsIndex < protoMessageInfo.numberOfHasBitsObjects();

    ProtoObject object = protoMessageInfo.getHasBitsObjects().get(hasBitsIndex);
    assert object.isProtoFieldObject();
    return object.asProtoFieldObject().getField();
  }

  /**
   * (One-of fields only.)
   *
   * <p>Java field identifying what the containing oneof is currently being used for.
   *
   * <p>The generated Java code for:
   *
   * <pre>
   *   message MyMessage {
   *     oneof my_oneof {
   *       int32 x = 123;
   *       ...
   *     }
   *   }
   * </pre>
   *
   * looks like:
   *
   * <pre>
   *   ... getMyOneofCase() { return myOneofCase_; }
   *   int getX() {
   *     if (myOneofCase_ == 123) return (Integer) myOneof_;
   *     return 0;
   *   }
   * </pre>
   */
  public DexField getOneOfCaseField(ProtoMessageInfo protoMessageInfo) {
    assert type.isOneOf();

    ProtoObject object = protoMessageInfo.getOneOfObjects().get(getAuxData()).getSecond();
    assert object.isProtoFieldObject();
    return object.asProtoFieldObject().getField();
  }

  /**
   * Data about the field as referenced from the Java implementation.
   *
   * <p>Java field into which the value is stored; constituents of a oneof all share the same
   * storage.
   */
  public DexField getValueStorage(ProtoMessageInfo protoMessageInfo) {
    ProtoObject object =
        type.isOneOf()
            ? protoMessageInfo.getOneOfObjects().get(getAuxData()).getFirst()
            : objects.get(0);
    assert object.isProtoFieldObject();
    return object.asProtoFieldObject().getField();
  }
}
