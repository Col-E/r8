// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import java.util.function.BiConsumer;

public class MethodProcessingId {

  private final int primaryId;
  private int secondaryId = 1;

  private MethodProcessingId(int primaryId) {
    this.primaryId = primaryId;
  }

  public String getAndIncrementId() {
    String id = getId();
    secondaryId++;
    return id;
  }

  public String getFullyQualifiedIdAndIncrement() {
    String id = getFullyQualifiedId();
    secondaryId++;
    return id;
  }

  public String getId() {
    if (secondaryId == 1) {
      return Integer.toString(primaryId);
    }
    return getFullyQualifiedId();
  }

  public String getFullyQualifiedId() {
    return primaryId + "$" + secondaryId;
  }

  public int getPrimaryId() {
    return primaryId;
  }

  public static class Factory {

    private final BiConsumer<ProgramMethod, MethodProcessingId> consumer;
    private int nextId = 1;

    public Factory() {
      this(null);
    }

    public Factory(BiConsumer<ProgramMethod, MethodProcessingId> consumer) {
      this.consumer = consumer;
    }

    public ReservedMethodProcessingIds reserveIds(SortedProgramMethodSet wave) {
      ReservedMethodProcessingIds result = new ReservedMethodProcessingIds(nextId, wave.size());
      nextId += wave.size();
      return result;
    }

    public class ReservedMethodProcessingIds {

      private final int firstReservedId;
      private final int numberOfReservedIds;

      private final ProgramMethodSet seen =
          InternalOptions.assertionsEnabled() ? ProgramMethodSet.createConcurrent() : null;

      public ReservedMethodProcessingIds(int firstReservedId, int numberOfReservedIds) {
        this.firstReservedId = firstReservedId;
        this.numberOfReservedIds = numberOfReservedIds;
      }

      public MethodProcessingId get(ProgramMethod method, int index) {
        assert index >= 0;
        assert index < numberOfReservedIds;
        assert seen.add(method);
        MethodProcessingId result = new MethodProcessingId(firstReservedId + index);
        if (consumer != null) {
          consumer.accept(method, result);
        }
        return result;
      }
    }
  }
}
