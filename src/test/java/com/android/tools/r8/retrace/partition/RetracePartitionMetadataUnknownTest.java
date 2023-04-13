// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.partition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.retrace.internal.MappingPartitionMetadataInternal;
import com.android.tools.r8.retrace.internal.RetracePartitionException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetracePartitionMetadataUnknownTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RetracePartitionMetadataUnknownTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws Exception {
    ByteArrayOutputStream temp = new ByteArrayOutputStream();
    DataOutputStream dataOutputStream = new DataOutputStream(temp);
    dataOutputStream.writeShort(Short.MAX_VALUE);
    dataOutputStream.close();
    byte[] bytes = temp.toByteArray();
    DiagnosticsHandler diagnosticsHandler = new DiagnosticsHandler() {};
    RetracePartitionException retracePartitionException =
        assertThrows(
            RetracePartitionException.class,
            () ->
                MappingPartitionMetadataInternal.deserialize(
                    bytes, MapVersion.MAP_VERSION_NONE, diagnosticsHandler));
    assertEquals(
        "Unknown map partition strategy for metadata", retracePartitionException.getMessage());
  }
}
