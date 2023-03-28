// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import com.android.tools.r8.graph.DexDebugInfoForWriting;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.utils.LebUtils;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class DebugBytecodeWriter {

  private final ObjectToOffsetMapping mapping;
  private final GraphLens graphLens;
  private final DexDebugInfoForWriting info;
  private ByteBuffer buffer;

  public DebugBytecodeWriter(
      DexDebugInfoForWriting info, ObjectToOffsetMapping mapping, GraphLens graphLens) {
    this.info = info;
    this.mapping = mapping;
    this.graphLens = graphLens;
    this.buffer = ByteBuffer.allocate(info.estimatedWriteSize());
  }

  public byte[] generate() {
    info.write(this, mapping, graphLens);
    return Arrays.copyOf(buffer.array(), buffer.position());
  }

  private void maybeGrow(int size) {
    if (buffer.remaining() < size) {
      ByteBuffer newBuffer = ByteBuffer.allocate(buffer.capacity() * 2);
      newBuffer.put(buffer.array(), 0, buffer.position());
      buffer = newBuffer;
    }
  }

  public void putByte(int item) {
    maybeGrow(1);
    buffer.put((byte) item);
  }

  public void putSleb128(int item) {
    byte[] encoded = LebUtils.encodeSleb128(item);
    maybeGrow(encoded.length);
    buffer.put(encoded, 0, encoded.length);
  }

  public void putUleb128(int item) {
    byte[] encoded = LebUtils.encodeUleb128(item);
    maybeGrow(encoded.length);
    buffer.put(encoded, 0, encoded.length);
  }

  private void putUleb128p1(int item) {
    putUleb128(item + 1);
  }

  private void putNoIndex() {
    putUleb128(0);
  }

  public void putType(DexType type) {
    if (type == null) {
      putNoIndex();
    } else {
      int index = mapping.getOffsetFor(type);
      putUleb128p1(index);
    }
  }

  public void putString(DexString string) {
    if (string == null) {
      putNoIndex();
    } else {
      int index = mapping.getOffsetFor(string);
      putUleb128p1(index);
    }
  }
}
