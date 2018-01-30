// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdwp;

import java.util.HashMap;
import java.util.Map;
import org.apache.harmony.jpda.tests.framework.jdwp.Method;
import org.apache.harmony.jpda.tests.framework.jdwp.ReplyPacket;
import org.apache.harmony.jpda.tests.jdwp.Method.JDWPMethodTestCase;
import org.apache.harmony.jpda.tests.share.JPDADebuggeeSynchronizer;

public class LineTableDuplicatesTest extends JDWPMethodTestCase {

  protected String getDebuggeeClassName() {
    return LineTableDuplicatesDebuggee.class.getName();
  }

  public void testLineTableDuplicatesTest001() {
    logWriter.println("testLineTableDuplicatesTest001 started");
    synchronizer.receiveMessage(JPDADebuggeeSynchronizer.SGNL_READY);

    long classID = getClassIDBySignature(getDebuggeeClassSignature());

    Method[] methodsInfo = debuggeeWrapper.vmMirror.getMethods(classID);
    assertEquals("Invalid number of methods", 5, methodsInfo.length);

    Map<String, Map<Integer, Integer>> methodLines = new HashMap<>();
    for (int i = 0; i < methodsInfo.length; i++) {
      String name = getMethodName(classID, methodsInfo[i].getMethodID());
      logWriter.println("method = " + name);
      ReplyPacket reply = getLineTable(classID, methodsInfo[i].getMethodID());
      long start = reply.getNextValueAsLong();
      logWriter.println("start = " + start);
      long end = reply.getNextValueAsLong();
      logWriter.println("end = " + end);
      int lines = reply.getNextValueAsInt();
      logWriter.println("lines = "+lines);
      Map<Integer, Integer> lineCounts = new HashMap<>();
      for (int j = 0; j < lines; j++) {
        long lineCodeIndex = reply.getNextValueAsLong();
        logWriter.println("lineCodeIndex = "+lineCodeIndex);
        int lineNumber = reply.getNextValueAsInt();
        logWriter.println("lineNumber = "+lineNumber);
        lineCounts.put(lineNumber, 1 + lineCounts.getOrDefault(lineNumber, 0));
      }
      methodLines.put(name, lineCounts);
    }

    assertEquals(
        "methodWithSingleLineMultipleLocals",
        1,
        methodLines.get("methodWithSingleLineMultipleLocals").getOrDefault(13, -1).intValue());

    assertEquals(
        "methodWithSingleLineMultipleDuplicates",
        2,
        methodLines.get("methodWithSingleLineMultipleDuplicates").getOrDefault(22, -1).intValue());

    synchronizer.sendMessage(JPDADebuggeeSynchronizer.SGNL_CONTINUE);
  }
}
