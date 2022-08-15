// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.softverification;

public class TestRunner {

  public static class Measure {
    private long start;
    private String description;

    public Measure() {}

    public void start(String description) {
      start = System.currentTimeMillis();
      this.description = description;
    }

    public String stop() {
      long end = System.currentTimeMillis();
      return "Time for " + description + " took: " + (end - start) + "\n";
    }
  }

  public static String run() {
    StringBuilder sb = new StringBuilder();
    Measure measure = new Measure();
    measure.start("InstanceSourceObject");
    TestInstanceOf.run();
    sb.append(measure.stop());
    measure.start("CheckCastSourceObject");
    TestCheckCast.run();
    sb.append(measure.stop());
    measure.start("TypeReference");
    TestTypeReference.run();
    sb.append(measure.stop());
    measure.start("NewInstance");
    TestNewInstance.run();
    sb.append(measure.stop());
    measure.start("StaticField");
    TestStaticField.run();
    sb.append(measure.stop());
    measure.start("StaticFieldWithOtherMembers");
    TestStaticFieldWithOtherMembers.run();
    sb.append(measure.stop());
    measure.start("StaticMethod");
    TestStaticMethod.run();
    sb.append(measure.stop());
    measure.start("InstanceField");
    TestInstanceField.run();
    sb.append(measure.stop());
    measure.start("InstanceFieldWithOtherMembers");
    TestInstanceFieldWithOtherMembers.run();
    sb.append(measure.stop());
    measure.start("HashCode");
    TestHashCode.run();
    sb.append(measure.stop());
    measure.start("InstanceMethod");
    TestInstanceMethod.run();
    sb.append(measure.stop());
    measure.start("TryCatch");
    TestTryCatch.run();
    sb.append(measure.stop());
    return sb.toString();
  }

  public static void main(String[] args) {
    System.out.println(run());
  }
}
