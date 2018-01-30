// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdwp;

import org.apache.harmony.jpda.tests.share.JPDADebuggeeSynchronizer;
import org.apache.harmony.jpda.tests.share.SyncDebuggee;

public class LineTableDuplicatesDebuggee extends SyncDebuggee {

    public void methodWithSingleLineMultipleLocals() {
        int x = 1; int y = 2; int z = 3; logWriter.println("Hello with values: " + x + y + z);
    }

    public void methodWithSingleLineMultipleDuplicates() {
        try {
            methodWithSingleLineMultipleLocals();
            methodWithSingleLineMultipleLocals();
            methodWithSingleLineMultipleLocals();
        } finally {
            logWriter.println("Line is duplicated on normal and exceptional exits");
        }
    }

    @Override
    public void run() {
        synchronizer.sendMessage(JPDADebuggeeSynchronizer.SGNL_READY);
        methodWithSingleLineMultipleDuplicates();
        synchronizer.receiveMessage(JPDADebuggeeSynchronizer.SGNL_CONTINUE);
    }

    public static void main(String [] args) {
        runDebuggee(LineTableDuplicatesDebuggee.class);
    }
}
