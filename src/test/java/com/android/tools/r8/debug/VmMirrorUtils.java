// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

import org.apache.harmony.jpda.tests.framework.jdwp.CommandPacket;
import org.apache.harmony.jpda.tests.framework.jdwp.JDWPCommands;
import org.apache.harmony.jpda.tests.framework.jdwp.JDWPCommands.ReferenceTypeCommandSet;
import org.apache.harmony.jpda.tests.framework.jdwp.JDWPConstants;
import org.apache.harmony.jpda.tests.framework.jdwp.JDWPConstants.Error;
import org.apache.harmony.jpda.tests.framework.jdwp.ReplyPacket;
import org.apache.harmony.jpda.tests.framework.jdwp.VmMirror;

/**
 * Utils for JDWP mirror.
 */
public abstract class VmMirrorUtils {

  private VmMirrorUtils() {
  }

  public static void checkReply(ReplyPacket replyPacket) {
    checkReply(replyPacket, Error.NONE);
  }

  public static void checkReply(ReplyPacket replyPacket, int expectedErrorCode) {
    if (replyPacket.getErrorCode() != expectedErrorCode) {
      throw new AssertionError(
          "Expected error code " + JDWPConstants.Error.getName(expectedErrorCode) + " ("
              + expectedErrorCode + ") but received " + JDWPConstants.Error
              .getName(expectedErrorCode) + " (" + expectedErrorCode + ")");
    }

  }

  public static String getMethodName(VmMirror mirror, long classID, long methodID) {
    CommandPacket packet = new CommandPacket(
        JDWPCommands.ReferenceTypeCommandSet.CommandSetID,
        ReferenceTypeCommandSet.MethodsWithGenericCommand);
    packet.setNextValueAsReferenceTypeID(classID);
    ReplyPacket reply = mirror.performCommand(packet);
    checkReply(reply);
    int methodsCount = reply.getNextValueAsInt();
    String result = null;
    for (int i = 0; i < methodsCount; i++) {
      long id = reply.getNextValueAsMethodID(); // skip method ID
      String methodName = reply.getNextValueAsString();
      reply.getNextValueAsString(); // skip signature
      reply.getNextValueAsString(); // skip generic signature
      reply.getNextValueAsInt(); // skip modifiers
      if (id == methodID) {
        result = methodName;
      }
    }
    assert reply.isAllDataRead();
    return result;
  }

  public static String getMethodSignature(VmMirror mirror, long classID, long methodID) {
    CommandPacket command = new CommandPacket(
        ReferenceTypeCommandSet.CommandSetID,
        ReferenceTypeCommandSet.MethodsWithGenericCommand);
    command.setNextValueAsReferenceTypeID(classID);
    ReplyPacket reply = mirror.performCommand(command);
    checkReply(reply);

    int methods = reply.getNextValueAsInt();
    for (int i = 0; i < methods; i++) {
      long mID = reply.getNextValueAsMethodID();
      reply.getNextValueAsString(); // skip method name
      String methodSign = reply.getNextValueAsString();
      reply.getNextValueAsString(); // skip generic signature
      reply.getNextValueAsInt(); // skip modifiers
      if (mID == methodID) {
        String value = methodSign.replaceAll("/", ".");
        int lastRoundBracketIndex = value.lastIndexOf(")");
        return value.substring(0, lastRoundBracketIndex + 1);
      }
    }

    assert reply.isAllDataRead();
    return null;
  }

}
