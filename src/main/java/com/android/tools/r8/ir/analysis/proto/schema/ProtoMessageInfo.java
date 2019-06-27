// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto.schema;

import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.Pair;
import java.util.ArrayList;
import java.util.List;

public class ProtoMessageInfo {

  public static class Builder {

    private int flags;

    private List<ProtoFieldInfo> fields;
    private List<Value> hasBitsObjects;
    private List<Pair<Value, Value>> oneOfObjects;

    public void setFlags(int value) {
      this.flags = value;
    }

    public void addField(ProtoFieldInfo field) {
      if (fields == null) {
        fields = new ArrayList<>();
      }
      fields.add(field);
    }

    public void addHasBitsObject(Value hasBitsObject) {
      if (hasBitsObjects == null) {
        hasBitsObjects = new ArrayList<>();
      }
      hasBitsObjects.add(hasBitsObject);
    }

    public void setNumberOfHasBitsObjects(int number) {
      if (number > 0) {
        hasBitsObjects = new ArrayList<>(number);
      }
    }

    public void addOneOfObject(Value first, Value second) {
      if (oneOfObjects == null) {
        oneOfObjects = new ArrayList<>();
      }
      oneOfObjects.add(new Pair<>(first, second));
    }

    public void setNumberOfOneOfObjects(int number) {
      if (number > 0) {
        oneOfObjects = new ArrayList<>(number);
      }
    }

    public ProtoMessageInfo build() {
      return new ProtoMessageInfo(flags, fields, hasBitsObjects, oneOfObjects);
    }
  }

  private final int flags;

  private final List<ProtoFieldInfo> fields;
  private final List<Value> hasBitsObjects;
  private final List<Pair<Value, Value>> oneOfObjects;

  private ProtoMessageInfo(
      int flags,
      List<ProtoFieldInfo> fields,
      List<Value> hasBitsObjects,
      List<Pair<Value, Value>> oneOfObjects) {
    this.flags = flags;
    this.fields = fields;
    this.hasBitsObjects = hasBitsObjects;
    this.oneOfObjects = oneOfObjects;
  }

  public static ProtoMessageInfo.Builder builder() {
    return new ProtoMessageInfo.Builder();
  }
}
