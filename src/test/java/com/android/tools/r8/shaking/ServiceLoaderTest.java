// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ServiceLoaderTest extends TestBase {

  private final boolean includeWorldGreeter;

  @Parameters(name = "Include WorldGreeter: {0}")
  public static Boolean[] data() {
    return BooleanUtils.values();
  }

  public ServiceLoaderTest(boolean includeWorldGreeter) {
    this.includeWorldGreeter = includeWorldGreeter;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = includeWorldGreeter ? "Hello world!" : "Hello";

    List<String> serviceImplementations = Lists.newArrayList(HelloGreeter.class.getTypeName());
    if (includeWorldGreeter) {
      serviceImplementations.add(WorldGreeter.class.getTypeName());
    }

    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addInnerClasses(ServiceLoaderTest.class)
            .addKeepMainRule(TestClass.class)
            // TODO(b/124181030): Test should work without the following keep-all-rules.
            .addKeepAllClassesRule()
            .addKeepAllInterfacesRule()
            .addDataEntryResources(
                DataEntryResource.fromBytes(
                    StringUtils.lines(serviceImplementations).getBytes(),
                    "META-INF/services/" + Greeter.class.getTypeName(),
                    Origin.unknown()))
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    // TODO(b/124181030): Verify that Greeter is merged into HelloGreeter when `includeWorldGreeter`
    //  is false.

    // TODO(b/124181030): Verify that META-INF/services/...WorldGreeter is removed when
    //  `includeWorldGreeter` is false.
  }

  static class TestClass {

    public static void main(String[] args) {
      for (Greeter greeter : ServiceLoader.load(Greeter.class)) {
        System.out.print(greeter.greeting());
      }
    }
  }

  public interface Greeter {

    String greeting();
  }

  public static class HelloGreeter implements Greeter {

    @Override
    public String greeting() {
      return "Hello";
    }
  }

  public static class WorldGreeter implements Greeter {

    @Override
    public String greeting() {
      return " world!";
    }
  }
}
