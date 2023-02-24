// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.retrace.PartitionMappingSupplier;
import com.android.tools.r8.retrace.Retrace;
import com.android.tools.r8.retrace.RetraceCommand;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RetracePartitionJoinNoMetadataTest extends RetraceApiTestBase {

  public RetracePartitionJoinNoMetadataTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  protected Class<? extends RetraceApiBinaryTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class ApiTest implements RetraceApiBinaryTest {

    private final String aMapping =
        "com.foo.bar.baz -> a:\n"
            + " # {'id':'sourceFile','fileName':'BarBaz.kt'}\n"
            + "  int field -> c\n"
            + "  1:1:void method():42:42 -> d";

    private final String bMapping =
        "foo -> foo:\n"
            + " # {'id':'sourceFile','fileName':'Foo-inlinee.kt'}\n"
            + "com.android.google.R8 -> b:\n"
            + " # {'id':'sourceFile','fileName':'R8Car.kt'}\n"
            + "  2:2:int foo.inlinee():43:43 -> f\n"
            + "  2:2:int otherMethod():44 -> f\n"
            + "# otherCommentHere";

    private final String exceptionMapping = "com.android.google.exception -> exception:\n";

    private final List<String> stackTrace =
        Arrays.asList("exception: Hello World!", " at a.d(Unknown:1)", " at b.f(Unknown:2)");

    private final List<String> expectedRetracedStackTrace =
        Arrays.asList(
            "com.android.google.exception: Hello World!",
            " at com.foo.bar.baz.method(BarBaz.kt:42)",
            " at foo.inlinee(Foo-inlinee.kt:43)",
            " at com.android.google.R8.otherMethod(R8Car.kt:44)");

    @Test
    public void test() {
      List<String> preFetch = new ArrayList<>();
      PartitionMappingSupplier mappingSupplier =
          PartitionMappingSupplier.noMetadataBuilder(MapVersion.MAP_VERSION_1_0)
              .setMappingPartitionFromKeySupplier(
                  key -> {
                    switch (key) {
                      case "a":
                        return aMapping.getBytes();
                      case "b":
                        return bMapping.getBytes();
                      case "exception":
                        return exceptionMapping.getBytes();
                      default:
                        fail();
                        return null;
                    }
                  })
              .setRegisterMappingPartitionCallback(preFetch::add)
              .build();
      Retrace.run(
          RetraceCommand.builder()
              .setStackTrace(stackTrace)
              .setMappingSupplier(mappingSupplier)
              .setRetracedStackTraceConsumer(
                  retraced -> assertEquals(expectedRetracedStackTrace, retraced))
              .build());
      assertEquals(3, preFetch.size());
      Set<String> expected = new HashSet<>(Arrays.asList("a", "b", "exception"));
      assertEquals(expected, new HashSet<>(preFetch));
    }
  }
}
