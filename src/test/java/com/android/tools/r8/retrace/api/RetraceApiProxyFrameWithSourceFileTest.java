// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.ProguardMappingSupplier;
import com.android.tools.r8.retrace.Retrace;
import com.android.tools.r8.retrace.RetraceStackFrameResult;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetraceStackTraceElementProxy;
import com.android.tools.r8.retrace.StackTraceElementProxy;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RetraceApiProxyFrameWithSourceFileTest extends RetraceApiTestBase {

  public RetraceApiProxyFrameWithSourceFileTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  protected Class<? extends RetraceApiBinaryTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class ApiTest implements RetraceApiBinaryTest {

    @Test
    public void test() {
      RetraceStackFrameResult<String> stringRetraceStackFrameResult =
          Retrace.<String, TestProxy>builder()
              .setMappingSupplier(
                  ProguardMappingSupplier.builder()
                      .setProguardMapProducer(ProguardMapProducer.fromString(""))
                      .build())
              .build()
              .retraceStackTraceParsed(
                  Collections.singletonList(new TestProxy()), RetraceStackTraceContext.empty())
              .getResult()
              .get(0)
              .get(0);
      assertEquals("com.android.tools.R8.a(Unknown Source)", stringRetraceStackFrameResult.get(0));
    }

    public static class TestProxy extends StackTraceElementProxy<String, TestProxy> {

      @Override
      public boolean hasClassName() {
        return true;
      }

      @Override
      public boolean hasMethodName() {
        return true;
      }

      @Override
      public boolean hasSourceFile() {
        return true;
      }

      @Override
      public boolean hasLineNumber() {
        return true;
      }

      @Override
      public boolean hasFieldName() {
        return false;
      }

      @Override
      public boolean hasFieldOrReturnType() {
        return false;
      }

      @Override
      public boolean hasMethodArguments() {
        return false;
      }

      @Override
      public ClassReference getClassReference() {
        return Reference.classFromTypeName("com.android.tools.R8");
      }

      @Override
      public String getMethodName() {
        return "a";
      }

      @Override
      public String getSourceFile() {
        return "Unknown Source";
      }

      @Override
      public int getLineNumber() {
        return 1;
      }

      @Override
      public String getFieldName() {
        return null;
      }

      @Override
      public String getFieldOrReturnType() {
        return null;
      }

      @Override
      public String getMethodArguments() {
        return null;
      }

      @Override
      public String toRetracedItem(
          RetraceStackTraceElementProxy<String, TestProxy> retracedProxy, boolean verbose) {
        assertFalse(retracedProxy.hasRetracedMethod());
        return retracedProxy.getRetracedClass().getTypeName()
            + "."
            + getMethodName()
            + "("
            + retracedProxy.getRetracedSourceFile().getOrInferSourceFile(getSourceFile())
            + ")";
      }
    }
  }
}
