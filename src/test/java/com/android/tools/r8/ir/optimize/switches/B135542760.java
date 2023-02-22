// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.switches;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B135542760 extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> {
              options.testing.enableSwitchToIfRewriting = false;
              options.testing.enableDeadSwitchCaseElimination = true;
            })
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(StringUtils.lines("[1, 2, 3, 4]"));
  }

  static class TestClass {

    public static final int x = System.currentTimeMillis() >= 0 ? 0 : 42;

    public static void main(String[] args) {
      byte[] ipAddress = textToNumericFormatV4("1.2.3.4");
      System.out.print("[");
      for (int i = 0; i < ipAddress.length; i++) {
        if (i > 0) {
          System.out.print(", ");
        }
        System.out.print(ipAddress[i]);
      }
      System.out.println("]");
    }

    // From https://android.googlesource.com/platform/libcore/+/765162affb50ad16a0bfe1f8afb8eb2874e62777/ojluni/src/main/java/sun/net/util/IPAddressUtil.java
    @NeverInline
    public static byte[] textToNumericFormatV4(String src)
    {
      byte[] res = new byte[4];

      long tmpValue = 0;
      int currByte = 0;
      boolean newOctet = true;

      int len = src.length();
      if (len == 0 || len > 15) {
        return null;
      }
      /*
       * When only one part is given, the value is stored directly in
       * the network address without any byte rearrangement.
       *
       * When a two part address is supplied, the last part is
       * interpreted as a 24-bit quantity and placed in the right
       * most three bytes of the network address. This makes the
       * two part address format convenient for specifying Class A
       * network addresses as net.host.
       *
       * When a three part address is specified, the last part is
       * interpreted as a 16-bit quantity and placed in the right
       * most two bytes of the network address. This makes the
       * three part address format convenient for specifying
       * Class B net- work addresses as 128.net.host.
       *
       * When four parts are specified, each is interpreted as a
       * byte of data and assigned, from left to right, to the
       * four bytes of an IPv4 address.
       *
       * We determine and parse the leading parts, if any, as single
       * byte values in one pass directly into the resulting byte[],
       * then the remainder is treated as a 8-to-32-bit entity and
       * translated into the remaining bytes in the array.
       */
      for (int i = 0; i < len; i++) {
        char c = src.charAt(i);
        if (c == '.') {
          if (newOctet || tmpValue < 0 || tmpValue > 0xff || currByte == 3) {
            return null;
          }
          res[currByte++] = (byte) (tmpValue & 0xff);
          tmpValue = 0;
          newOctet = true;
        } else {
          int digit = Character.digit(c, 10);
          if (digit < 0) {
            return null;
          }
          tmpValue *= 10;
          tmpValue += digit;
          newOctet = false;
        }
      }
      if (newOctet || tmpValue < 0 || tmpValue >= (1L << ((4 - currByte) * 8))) {
        return null;
      }
      switch (currByte) {
        // BEGIN Android-changed: Require all four parts to be given for an IPv4 address.
            /*
            case 0:
                res[0] = (byte) ((tmpValue >> 24) & 0xff);
            case 1:
                res[1] = (byte) ((tmpValue >> 16) & 0xff);
            case 2:
                res[2] = (byte) ((tmpValue >>  8) & 0xff);
            */
        case 0:
        case 1:
        case 2:
          return null;
        // END Android-changed: Require all four parts to be given for an IPv4 address.
        case 3:
          res[3] = (byte) ((tmpValue >>  0) & 0xff);
      }
      return res;
    }
  }
}
