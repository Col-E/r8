// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.RetraceFieldElement;
import com.android.tools.r8.retrace.RetraceFieldResult;
import com.android.tools.r8.retrace.Retracer;
import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class MemberFieldOverlapStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException", "\tat a.A.a(Bar.java:1)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines("foo.Bar -> a.A:", "  1:1:int method():5 -> a", "  int field -> a");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat foo.Bar.method(Bar.java:5)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat foo.Bar.int method()(Bar.java:5)");
  }

  @Override
  public int expectedWarnings() {
    return 1;
  }

  public void inspectField(Retracer retracer) {
    RetraceFieldResult result =
        retracer.retraceClass(Reference.classFromTypeName("a.A")).lookupField("a");
    assertFalse(result.isAmbiguous());
    assertEquals(1, result.stream().count());
    Optional<? extends RetraceFieldElement> field = result.stream().findFirst();
    assertTrue(field.isPresent());
    assertEquals("field", field.get().getField().getFieldName());
  }
}
