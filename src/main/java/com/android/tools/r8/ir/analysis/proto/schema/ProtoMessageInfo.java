// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto.schema;

import static com.android.tools.r8.ir.analysis.proto.RawMessageInfoDecoder.IS_PROTO_2_MASK;

import com.android.tools.r8.utils.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

public class ProtoMessageInfo {

  public static final int BITS_PER_HAS_BITS_WORD = 32;

  public static class Builder {

    private int flags;

    private LinkedList<ProtoFieldInfo> fields;
    private LinkedList<ProtoObject> hasBitsObjects;
    private LinkedList<Pair<ProtoObject, ProtoObject>> oneOfObjects;

    public void setFlags(int value) {
      this.flags = value;
    }

    public void addField(ProtoFieldInfo field) {
      if (fields == null) {
        fields = new LinkedList<>();
      }
      fields.add(field);
    }

    public void addHasBitsObject(ProtoObject hasBitsObject) {
      if (hasBitsObjects == null) {
        hasBitsObjects = new LinkedList<>();
      }
      hasBitsObjects.add(hasBitsObject);
    }

    public void addOneOfObject(ProtoObject first, ProtoObject second) {
      if (oneOfObjects == null) {
        oneOfObjects = new LinkedList<>();
      }
      oneOfObjects.add(new Pair<>(first, second));
    }

    public ProtoMessageInfo build() {
      removeDeadFields();
      removeUnusedSharedData();
      return new ProtoMessageInfo(flags, fields, hasBitsObjects, oneOfObjects);
    }

    private void removeDeadFields() {
      if (fields != null) {
        Predicate<ProtoFieldInfo> isFieldDead =
            field -> {
              ProtoObject object =
                  field.getType().isOneOf()
                      ? oneOfObjects.get(field.getAuxData()).getFirst()
                      : field.getObjects().get(0);
              return object.isDeadProtoFieldObject();
            };
        fields.removeIf(isFieldDead);
      }
    }

    private void removeUnusedSharedData() {
      // Gather used "oneof" and "hasbits" indices.
      IntList usedOneofIndices = new IntArrayList();
      IntList usedHasBitsIndices = new IntArrayList();
      if (fields != null) {
        for (ProtoFieldInfo field : fields) {
          if (field.hasAuxData()) {
            if (field.getType().isOneOf()) {
              usedOneofIndices.add(field.getAuxData());
            } else {
              usedHasBitsIndices.add(field.getAuxData() / BITS_PER_HAS_BITS_WORD);
            }
          }
        }
      }

      // Remove unused parts of "oneof" vector.
      if (oneOfObjects != null) {
        Iterator<Pair<ProtoObject, ProtoObject>> oneOfObjectIterator = oneOfObjects.iterator();
        for (int i = 0; i < oneOfObjects.size(); i++) {
          oneOfObjectIterator.next();
          if (!usedOneofIndices.contains(i)) {
            oneOfObjectIterator.remove();
          }
        }
      }

      // Remove unused parts of "hasbits" vector.
      if (hasBitsObjects != null) {
        Iterator<ProtoObject> hasBitsObjectIterator = hasBitsObjects.iterator();
        for (int i = 0; i < hasBitsObjects.size(); i++) {
          hasBitsObjectIterator.next();
          if (!usedHasBitsIndices.contains(i)) {
            hasBitsObjectIterator.remove();
          }
        }
      }

      // TODO(b/112437944): Fix up references + add a test that fails when references are not fixed.
    }
  }

  private final int flags;

  private final LinkedList<ProtoFieldInfo> fields;
  private final LinkedList<ProtoObject> hasBitsObjects;
  private final LinkedList<Pair<ProtoObject, ProtoObject>> oneOfObjects;

  private ProtoMessageInfo(
      int flags,
      LinkedList<ProtoFieldInfo> fields,
      LinkedList<ProtoObject> hasBitsObjects,
      LinkedList<Pair<ProtoObject, ProtoObject>> oneOfObjects) {
    this.flags = flags;
    this.fields = fields;
    this.hasBitsObjects = hasBitsObjects;
    this.oneOfObjects = oneOfObjects;
  }

  public static ProtoMessageInfo.Builder builder() {
    return new ProtoMessageInfo.Builder();
  }

  public boolean isProto2() {
    return (flags & IS_PROTO_2_MASK) != 0;
  }

  public List<ProtoFieldInfo> getFields() {
    return fields;
  }

  public int getFlags() {
    return flags;
  }

  public List<ProtoObject> getHasBitsObjects() {
    return hasBitsObjects;
  }

  public List<Pair<ProtoObject, ProtoObject>> getOneOfObjects() {
    return oneOfObjects;
  }

  public boolean hasFields() {
    return fields != null && !fields.isEmpty();
  }

  public int numberOfFields() {
    return fields != null ? fields.size() : 0;
  }

  public int numberOfHasBitsObjects() {
    return hasBitsObjects != null ? hasBitsObjects.size() : 0;
  }

  public int numberOfOneOfObjects() {
    return oneOfObjects != null ? oneOfObjects.size() : 0;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder("ProtoMessageInfo(fields=[");
    if (hasFields()) {
      Iterator<ProtoFieldInfo> fieldIterator = fields.iterator();
      builder.append(fieldIterator.next());
      while (fieldIterator.hasNext()) {
        builder.append(", ").append(fieldIterator.next());
      }
    }
    return builder.append("])").toString();
  }
}
