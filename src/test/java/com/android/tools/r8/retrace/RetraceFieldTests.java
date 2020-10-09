// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.retrace.mappings.FieldsWithSameMinifiedNameMapping;
import com.android.tools.r8.retrace.mappings.MappingForTest;
import java.util.function.Consumer;
import org.junit.Test;

public class RetraceFieldTests {

  @Test
  public void testFieldsWithSameMinifiedName() throws Exception {
    FieldsWithSameMinifiedNameMapping mapping = new FieldsWithSameMinifiedNameMapping();
    runRetraceTest(mapping, mapping::inspect);
  }

  private void runRetraceTest(MappingForTest mappingForTest, Consumer<RetraceApi> inspection)
      throws Exception {
    inspection.accept(Retracer.create(mappingForTest::mapping, new TestDiagnosticMessagesImpl()));
  }
}
