// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

/**
 * Wraps DexDebugInfo to make comparison and hashcode not consider
 * the SetInlineFrames
 */
public class DexDebugInfoForWriting extends DexDebugInfo {

  private DexDebugInfoForWriting(int startLine, DexString[] parameters, DexDebugEvent[] events) {
    super(startLine, parameters, events);
  }

  public static DexDebugInfoForWriting create(DexDebugInfo debugInfo) {
    assert debugInfo != null;
    int nonWritableEvents = 0;
    for (DexDebugEvent event : debugInfo.events) {
      if (!event.isWritableEvent()) {
        nonWritableEvents++;
      }
    }
    DexDebugEvent[] writableEvents;
    if (nonWritableEvents == 0) {
      writableEvents = debugInfo.events;
    } else {
      writableEvents = new DexDebugEvent[debugInfo.events.length - nonWritableEvents];
      int i = 0;
      for (DexDebugEvent event : debugInfo.events) {
        if (event.isWritableEvent()) {
          writableEvents[i++] = event;
        }
      }
    }
    return new DexDebugInfoForWriting(debugInfo.startLine, debugInfo.parameters, writableEvents);
  }
}
