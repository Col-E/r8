// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto.schema;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.proto.ProtoUtils;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

public class ProtoMessageInfo {

  public static final int BITS_PER_HAS_BITS_WORD = 32;

  public static class Builder {

    private final ProgramMethod dynamicMethod;

    private int flags;

    // TODO(b/270398965): Replace LinkedList.
    @SuppressWarnings("JdkObsolete")
    private LinkedList<ProtoFieldInfo> fields;
    // TODO(b/270398965): Replace LinkedList.
    @SuppressWarnings("JdkObsolete")
    private LinkedList<ProtoFieldObject> hasBitsObjects;
    // TODO(b/270398965): Replace LinkedList.
    @SuppressWarnings("JdkObsolete")
    private LinkedList<ProtoOneOfObjectPair> oneOfObjects;

    private Builder(ProgramMethod dynamicMethod) {
      this.dynamicMethod = dynamicMethod;
    }

    public void setFlags(int value) {
      this.flags = value;
    }

    // TODO(b/270398965): Replace LinkedList.
    @SuppressWarnings("JdkObsolete")
    public void addField(ProtoFieldInfo field) {
      if (fields == null) {
        fields = new LinkedList<>();
      }
      fields.add(field);
    }

    // TODO(b/270398965): Replace LinkedList.
    @SuppressWarnings("JdkObsolete")
    public void addHasBitsObject(ProtoFieldObject hasBitsObject) {
      if (hasBitsObjects == null) {
        hasBitsObjects = new LinkedList<>();
      }
      hasBitsObjects.add(hasBitsObject);
    }

    // TODO(b/270398965): Replace LinkedList.
    @SuppressWarnings("JdkObsolete")
    public void addOneOfObject(ProtoFieldObject oneOfObject, ProtoFieldObject oneOfCaseObject) {
      if (oneOfObjects == null) {
        oneOfObjects = new LinkedList<>();
      }
      oneOfObjects.add(new ProtoOneOfObjectPair(oneOfObject, oneOfCaseObject));
    }

    public ProtoMessageInfo build() throws ProtoMessageInfoBuilderException {
      removeDeadFields();
      removeUnusedSharedData();
      return new ProtoMessageInfo(dynamicMethod, flags, fields, hasBitsObjects, oneOfObjects);
    }

    private void removeDeadFields() {
      if (fields != null) {
        Predicate<ProtoFieldInfo> isFieldDead =
            field -> {
              ProtoObject object =
                  field.getType().isOneOf()
                      ? oneOfObjects.get(field.getAuxData()).getOneOfObject()
                      : field.getObjects().get(0);
              return object.isDeadProtoFieldObject();
            };
        fields.removeIf(isFieldDead);
      }
    }

    private void removeUnusedSharedData() throws ProtoMessageInfoBuilderException {
      if (fields == null || fields.isEmpty()) {
        oneOfObjects = null;
        hasBitsObjects = null;
        return;
      }

      // Gather used "oneof" and "hasbits" indices.
      IntSet usedOneOfIndices = new IntOpenHashSet();
      IntSet usedHasBitsIndices = new IntOpenHashSet();
      for (ProtoFieldInfo field : fields) {
        if (field.hasAuxData()) {
          if (field.getType().isOneOf()) {
            usedOneOfIndices.add(field.getAuxData());
          } else {
            usedHasBitsIndices.add(field.getAuxData() / BITS_PER_HAS_BITS_WORD);
          }
        }
      }

      if (hasBitsObjects != null) {
        for (int i = 0; i < hasBitsObjects.size(); i++) {
          ProtoFieldObject hasBitsObject = hasBitsObjects.get(i);
          if (hasBitsObject.isLiveProtoFieldObject()) {
            usedHasBitsIndices.add(i);
          }
        }
      }

      // Remove unused parts of "oneof" vector.
      Int2IntMap newOneOfObjectIndices = new Int2IntArrayMap();
      if (oneOfObjects != null) {
        Iterator<ProtoOneOfObjectPair> oneOfObjectIterator = oneOfObjects.iterator();
        int i = 0;
        int numberOfRemovedOneOfObjects = 0;
        while (oneOfObjectIterator.hasNext()) {
          oneOfObjectIterator.next();
          if (usedOneOfIndices.contains(i)) {
            newOneOfObjectIndices.put(i, i - numberOfRemovedOneOfObjects);
          } else {
            oneOfObjectIterator.remove();
            numberOfRemovedOneOfObjects++;
          }
          i++;
        }

        assert oneOfObjects.stream()
            .allMatch(
                oneOfObjectPair ->
                    oneOfObjectPair.stream().noneMatch(ProtoObject::isDeadProtoFieldObject));
      }

      // Remove unused parts of "hasbits" vector.
      Int2IntMap newHasBitsObjectIndices = new Int2IntArrayMap();
      if (hasBitsObjects != null) {
        Iterator<ProtoFieldObject> hasBitsObjectIterator = hasBitsObjects.iterator();
        int i = 0;
        int numberOfRemovedHasBitsObjects = 0;
        while (hasBitsObjectIterator.hasNext()) {
          hasBitsObjectIterator.next();
          if (usedHasBitsIndices.contains(i)) {
            newHasBitsObjectIndices.put(i, i - numberOfRemovedHasBitsObjects);
          } else {
            hasBitsObjectIterator.remove();
            numberOfRemovedHasBitsObjects++;
          }
          i++;
        }

        // We should not have any bits pointing to dead proto fields.
        for (ProtoFieldObject hasBitsObject : hasBitsObjects) {
          if (hasBitsObject.isDeadProtoFieldObject()) {
            throw new ProtoMessageInfoBuilderException();
          }
        }

        assert hasBitsObjects.stream().noneMatch(ProtoFieldObject::isDeadProtoFieldObject);
      }

      // Fix up references.
      for (ProtoFieldInfo field : fields) {
        if (field.hasAuxData()) {
          if (field.getType().isOneOf()) {
            field.setAuxData(newOneOfObjectIndices.get(field.getAuxData()));
          } else {
            int auxData = field.getAuxData();
            int oldHasBitsObjectIndex = auxData / BITS_PER_HAS_BITS_WORD;
            int oldHasBitsObjectBitIndex = auxData % BITS_PER_HAS_BITS_WORD;
            assert newHasBitsObjectIndices.containsKey(oldHasBitsObjectIndex);
            field.setAuxData(
                newHasBitsObjectIndices.get(oldHasBitsObjectIndex) * BITS_PER_HAS_BITS_WORD
                    + oldHasBitsObjectBitIndex);
          }
        }
      }
    }
  }

  public static class ProtoMessageInfoBuilderException extends Exception {
    private ProtoMessageInfoBuilderException() {}
  }

  private final ProgramMethod dynamicMethod;
  private final int flags;

  private final LinkedList<ProtoFieldInfo> fields;
  private final LinkedList<ProtoFieldObject> hasBitsObjects;
  private final LinkedList<ProtoOneOfObjectPair> oneOfObjects;

  private ProtoMessageInfo(
      ProgramMethod dynamicMethod,
      int flags,
      LinkedList<ProtoFieldInfo> fields,
      LinkedList<ProtoFieldObject> hasBitsObjects,
      LinkedList<ProtoOneOfObjectPair> oneOfObjects) {
    this.dynamicMethod = dynamicMethod;
    this.flags = flags;
    this.fields = fields;
    this.hasBitsObjects = hasBitsObjects;
    this.oneOfObjects = oneOfObjects;
  }

  public static ProtoMessageInfo.Builder builder(ProgramMethod dynamicMethod) {
    return new ProtoMessageInfo.Builder(dynamicMethod);
  }

  public boolean isProto2() {
    return ProtoUtils.isProto2(flags);
  }

  public ProgramMethod getDynamicMethod() {
    return dynamicMethod;
  }

  public List<ProtoFieldInfo> getFields() {
    return fields;
  }

  public int getFlags() {
    return flags;
  }

  public List<ProtoFieldObject> getHasBitsObjects() {
    return hasBitsObjects;
  }

  public List<ProtoOneOfObjectPair> getOneOfObjects() {
    return oneOfObjects;
  }

  public DexType getType() {
    return dynamicMethod.getHolderType();
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
