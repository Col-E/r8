// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto.schema;

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
}
