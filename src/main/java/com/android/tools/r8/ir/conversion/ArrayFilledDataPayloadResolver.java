// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.dex.code.DexFillArrayData;
import com.android.tools.r8.dex.code.DexFillArrayDataPayload;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for resolving payload information during IR construction.
 */
public class ArrayFilledDataPayloadResolver {

  private static class PayloadData {
    public int element_width;
    public long size;
    public short[] data;
  }

  private final Map<Integer, DexFillArrayDataPayload> unresolvedPayload = new HashMap<>();
  private final Map<Integer, PayloadData> payloadToData = new HashMap<>();

  public void addPayloadUser(DexFillArrayData dex) {
    int offset = dex.getOffset();
    int payloadOffset = offset + dex.getPayloadOffset();
    assert !payloadToData.containsKey(payloadOffset);
    payloadToData.put(payloadOffset, new PayloadData());
    if (unresolvedPayload.containsKey(payloadOffset)) {
      DexFillArrayDataPayload payload = unresolvedPayload.remove(payloadOffset);
      resolve(payload);
    }
  }

  public void resolve(DexFillArrayDataPayload payload) {
    int payloadOffset = payload.getOffset();
    PayloadData data = payloadToData.get(payloadOffset);
    if (data == null) {
      unresolvedPayload.put(payloadOffset, payload);
      return;
    }

    data.element_width = payload.element_width;
    data.size = payload.size;
    data.data = payload.data;
  }

  public int getElementWidth(int payloadOffset) {
    return payloadToData.get(payloadOffset).element_width;
  }

  public long getSize(int payloadOffset) {
    return payloadToData.get(payloadOffset).size;
  }

  public short[] getData(int payloadOffset) {
    return payloadToData.get(payloadOffset).data;
  }

  public void clear() {
    payloadToData.clear();
  }
}
