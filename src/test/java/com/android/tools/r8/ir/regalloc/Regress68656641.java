// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueNumberGenerator;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.smali.SmaliBuilder.MethodSignature;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import java.util.PriorityQueue;
import org.junit.Test;

public class Regress68656641 extends SmaliTestBase {

  private static class MyRegisterAllocator extends LinearScanRegisterAllocator {
    public MyRegisterAllocator(IRCode code, InternalOptions options) {
      super(code, options);
    }

    public void addInactiveIntervals(LiveIntervals intervals) {
      inactive.add(intervals);
    }

    public void splitOverlappingInactiveIntervals(LiveIntervals intervals, int register) {
      splitOverlappingInactiveIntervals(intervals, register, false);
    }

    public PriorityQueue<LiveIntervals> getUnhandled() {
      return unhandled;
    }
  }

  IRCode simpleCode(InternalOptions options) throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);
    MethodSignature signature = builder.addStaticMethod(
        "void",
        DEFAULT_METHOD_NAME,
        ImmutableList.of(),
        1,
        "    return-void");
    AndroidApp application = buildApplication(builder);
    // Build the code, and split the code into three blocks.
    ValueNumberGenerator valueNumberGenerator = new ValueNumberGenerator();
    DexEncodedMethod method = getMethod(application, signature);
    IRCode code = method.buildInliningIRForTesting(new InternalOptions(), valueNumberGenerator);
    return code;
  }


  @Test
  public void splitOverlappingInactiveIntervalWithNoNextUse() throws Exception {
    InternalOptions options = new InternalOptions();
    IRCode code = simpleCode(options);
    MyRegisterAllocator allocator = new MyRegisterAllocator(code, options);
    // Setup live an inactive live interval with ranges [0, 10[ and [20, 30[ with only
    // uses in the first interval and which is linked to another interval.
    LiveIntervals inactiveIntervals =
        new LiveIntervals(new Value(0, TypeLatticeElement.INT, null));
    inactiveIntervals.addRange(new LiveRange(0, 10));
    inactiveIntervals.addUse(new LiveIntervalsUse(0, 10));
    inactiveIntervals.addUse(new LiveIntervalsUse(4, 10));
    inactiveIntervals.addRange(new LiveRange(20, 30));
    inactiveIntervals.setRegister(0);
    LiveIntervals linked =
        new LiveIntervals(new Value(1, TypeLatticeElement.INT, null));
    linked.setRegister(1);
    inactiveIntervals.link(linked);
    allocator.addInactiveIntervals(inactiveIntervals);
    // Setup an unhandled interval that overlaps the inactive interval.
    LiveIntervals unhandledIntervals =
        new LiveIntervals(new Value(2, TypeLatticeElement.INT, null));
    unhandledIntervals.addRange(new LiveRange(12, 24));
    // Split the overlapping inactive intervals and check that after the split, the second
    // part of the inactive interval is unhandled and will therefore get a new register
    // assigned later during allocation.
    allocator.splitOverlappingInactiveIntervals(unhandledIntervals, 0);
    assert allocator.getUnhandled().size() == 1;
    assert allocator.getUnhandled().peek().getStart() == 20;
    assert allocator.getUnhandled().peek().getEnd() == 30;
  }
}
