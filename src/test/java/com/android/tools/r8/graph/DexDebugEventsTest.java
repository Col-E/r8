// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.graph.DexDebugEvent.AdvancePC;
import com.android.tools.r8.graph.DexDebugEvent.Default;
import com.android.tools.r8.ir.code.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;

public class DexDebugEventsTest {

  static final DexItemFactory factory = new DexItemFactory();
  static final DexMethod method = factory.createMethod(
      factory.objectDescriptor,
      factory.createString("foo"),
      factory.voidDescriptor,
      DexString.EMPTY_ARRAY);

  @Test
  public void testSpecialOpcodeRepresentation() {
    {
      // Smallest numeric opcode.
      Default minDefault = new Default(0x0a);
      assertEquals(0, minDefault.getPCDelta());
      assertEquals(-4, minDefault.getLineDelta());
    }

    {
      // Largest numeric opcode.
      Default maxDefault = new Default(0xff);
      assertEquals(16, maxDefault.getPCDelta());
      assertEquals(1, maxDefault.getLineDelta());
    }

    {
      // PC range of 16, smallest value for line is -4 delta.
      Default maxPcDefault = new Default(0xff - 5);
      assertEquals(16, maxPcDefault.getPCDelta());
      assertEquals(-4, maxPcDefault.getLineDelta());
    }

    {
      // Maximum line range is 15 points ranging from -4 to 10.
      Default maxLineDefault = new Default(0x0a + 14);
      assertEquals(0, maxLineDefault.getPCDelta());
      assertEquals(10, maxLineDefault.getLineDelta());
    }

    // For a PC advance of 16, only the line range -4 to 1 can be represented.
    for (int i = 0; i < 6; i++) {
      Default maxPcAndRangeDefault = new Default(0xff - i);
      assertEquals(16, maxPcAndRangeDefault.getPCDelta());
      assertEquals(1 - i, maxPcAndRangeDefault.getLineDelta());
    }

    {
      // For a line advance of 10 the max PC is 15.
      Default maxPcIfUsingMaxLineDefault = new Default(0xff - 6);
      assertEquals(15, maxPcIfUsingMaxLineDefault.getPCDelta());
      assertEquals(10, maxPcIfUsingMaxLineDefault.getLineDelta());
    }
  }

  @Test
  public void testMaxPcMinLine() {
    testAdvancement(0, 16, 5, 1, events -> {
      assertEquals(1, events.size());
      assertTrue(events.get(0) instanceof Default);
      Default event = (Default) events.get(0);
      assertEquals(16, event.getPCDelta());
      assertEquals(-4, event.getLineDelta());
    });
  }

  @Test
  public void testMaxPcRemainingLine() {
    testAdvancement(0, 16, 1, 2, events -> {
      assertEquals(1, events.size());
      assertTrue(events.get(0) instanceof Default);
      Default event = (Default) events.get(0);
      assertEquals(16, event.getPCDelta());
      assertEquals(1, event.getLineDelta());
    });
  }

  @Test
  public void testMaxPcPlusOne() {
    testAdvancement(0, 17, 1, 2, events -> {
      assertEquals(2, events.size());
      assertTrue(events.get(0) instanceof AdvancePC);
      assertTrue(events.get(1) instanceof Default);
      AdvancePC advancePC = (AdvancePC) events.get(0);
      Default aDefault = (Default) events.get(1);
      assertEquals(17, advancePC.delta + aDefault.getPCDelta());
      assertEquals(1, aDefault.getLineDelta());
    });
  }

  @Test
  public void testMaxPcMaxLine() {
    // If line delta is larger that 1 then there is not room for a pc delta of 16.
    testAdvancement(0, 16, 1, 11, events -> {
      assertEquals(2, events.size());
      assertTrue(events.get(0) instanceof AdvancePC);
      assertTrue(events.get(1) instanceof Default);
      AdvancePC advancePC = (AdvancePC) events.get(0);
      Default aDefault = (Default) events.get(1);
      assertEquals(16, advancePC.delta + aDefault.getPCDelta());
      assertEquals(10, aDefault.getLineDelta());
    });
  }

  @Test
  public void testMaxLineRemainingPc() {
    testAdvancement(0, 15, 1, 11, events -> {
      assertEquals(1, events.size());
      assertTrue(events.get(0) instanceof Default);
      Default event = (Default) events.get(0);
      assertEquals(15, event.getPCDelta());
      assertEquals(10, event.getLineDelta());
    });
  }

  private void testAdvancement(
      int pc, int nextPc, int line, int nextLine, Consumer<List<DexDebugEvent>> consumer) {
    List<DexDebugEvent> events = new ArrayList<>();
    DexDebugEventBuilder.emitAdvancementEvents(
        pc,
        new Position(line, null, method, null),
        nextPc,
        new Position(nextLine, null, method, null),
        events,
        factory);
    consumer.accept(events);
  }
}
