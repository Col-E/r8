// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.graph.DexDebugEvent;
import com.android.tools.r8.graph.DexDebugEvent.SetPositionFrame;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexDebugInfo.EventBasedDebugInfo;
import com.android.tools.r8.graph.DexDebugPositionState;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.utils.DexDebugUtils.PositionInfo.PositionInfoBuilder;
import java.util.List;

public class DexDebugUtils {

  public static boolean verifySetPositionFramesFollowedByDefaultEvent(DexDebugInfo debugInfo) {
    return debugInfo == null
        || debugInfo.isPcBasedInfo()
        || verifySetPositionFramesFollowedByDefaultEvent(debugInfo.asEventBasedInfo().events);
  }

  public static boolean verifySetPositionFramesFollowedByDefaultEvent(List<DexDebugEvent> events) {
    return verifySetPositionFramesFollowedByDefaultEvent(events.toArray(DexDebugEvent.EMPTY_ARRAY));
  }

  public static boolean verifySetPositionFramesFollowedByDefaultEvent(DexDebugEvent... events) {
    for (int i = events.length - 1; i >= 0; i--) {
      if (events[i].isDefaultEvent()) {
        return true;
      }
      assert !events[i].isPositionFrame();
    }
    return true;
  }

  public static PositionInfo computePreamblePosition(
      DexMethod method, boolean isD8R8Synthesized, EventBasedDebugInfo debugInfo) {
    if (debugInfo == null) {
      return PositionInfo.builder().build();
    }
    Box<Position> existingPositionFrame = new Box<>();
    DexDebugPositionState visitor =
        new DexDebugPositionState(debugInfo.startLine, method, isD8R8Synthesized) {
          @Override
          public void visit(SetPositionFrame setPositionFrame) {
            super.visit(setPositionFrame);
            existingPositionFrame.set(setPositionFrame.getPosition());
          }
        };
    PositionInfoBuilder builder = PositionInfo.builder();
    for (DexDebugEvent event : debugInfo.events) {
      event.accept(visitor);
      if (visitor.getCurrentPc() > 0) {
        break;
      }
      if (event.isDefaultEvent()) {
        builder.setLinePositionAtPcZero(visitor.getCurrentLine());
        builder.setFramePosition(existingPositionFrame.get());
      }
    }
    return builder.build();
  }

  public static class PositionInfo {

    private final Position framePosition;
    private final int linePositionAtPcZero;

    private PositionInfo(Position framePosition, int linePositionAtPcZero) {
      this.framePosition = framePosition;
      this.linePositionAtPcZero = linePositionAtPcZero;
    }

    public boolean hasFramePosition() {
      return framePosition != null;
    }

    public boolean hasLinePositionAtPcZero() {
      return linePositionAtPcZero > -1;
    }

    public Position getFramePosition() {
      return framePosition;
    }

    public int getLinePositionAtPcZero() {
      return linePositionAtPcZero;
    }

    public static PositionInfoBuilder builder() {
      return new PositionInfoBuilder();
    }

    public static class PositionInfoBuilder {

      private Position framePosition;
      private int linePositionAtPcZero = -1;

      public void setFramePosition(Position position) {
        this.framePosition = position;
      }

      public void setLinePositionAtPcZero(int linePositionAtPcZero) {
        this.linePositionAtPcZero = linePositionAtPcZero;
      }

      public PositionInfo build() {
        return new PositionInfo(framePosition, linePositionAtPcZero);
      }
    }
  }
}
