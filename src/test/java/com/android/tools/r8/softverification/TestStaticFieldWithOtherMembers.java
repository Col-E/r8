// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.softverification;

public class TestStaticFieldWithOtherMembers {

  public static String run() {
    if (System.currentTimeMillis() == 0) {
      System.out.println(MissingClass.staticField);
    }
    if (System.currentTimeMillis() == 0) {
      System.out.println(MissingClass.staticField);
    }
    String currentString = "foobar";
    for (int i = 0; i < 10; i++) {
      currentString = "foobar" + (i + currentString.length());
    }
    return currentString;
  }

  public static String run1(MissingClass missingClass) {
    if (System.currentTimeMillis() == 0) {
      missingClass.instanceMethod();
      return "foo";
    }
    return "bar";
  }

  public static String run2(MissingClass missingClass) {
    if (System.currentTimeMillis() == 0) {
      missingClass.instanceMethod();
      return "foo";
    }
    return "bar";
  }

  public static String run3(MissingClass missingClass) {
    if (System.currentTimeMillis() == 0) {
      missingClass.instanceMethod();
      return "foo";
    }
    return "bar";
  }

  public static String run4(MissingClass missingClass) {
    if (System.currentTimeMillis() == 0) {
      missingClass.instanceMethod();
      return "foo";
    }
    return "bar";
  }

  public static String run5(MissingClass missingClass) {
    if (System.currentTimeMillis() == 0) {
      missingClass.instanceMethod();
      return "foo";
    }
    return "bar";
  }

  public static String run6(MissingClass missingClass) {
    if (System.currentTimeMillis() == 0) {
      missingClass.instanceMethod();
      return "foo";
    }
    return "bar";
  }

  public static String run7(MissingClass missingClass) {
    if (System.currentTimeMillis() == 0) {
      missingClass.instanceMethod();
      return "foo";
    }
    return "bar";
  }

  public static String run8(MissingClass missingClass) {
    if (System.currentTimeMillis() == 0) {
      missingClass.instanceMethod();
      return "foo";
    }
    return "bar";
  }

  public static String run9(MissingClass missingClass) {
    if (System.currentTimeMillis() == 0) {
      missingClass.instanceMethod();
      return "foo";
    }
    return "bar";
  }

  public static String run10(MissingClass missingClass) {
    if (System.currentTimeMillis() == 0) {
      missingClass.instanceMethod();
      return "foo";
    }
    return "bar";
  }

  public static String run11(MissingClass missingClass) {
    if (System.currentTimeMillis() == 0) {
      missingClass.instanceMethod();
      return "foo";
    }
    return "bar";
  }

  public static String run12(MissingClass missingClass) {
    if (System.currentTimeMillis() == 0) {
      missingClass.instanceMethod();
      return "foo";
    }
    return "bar";
  }

  public static String run13(MissingClass missingClass) {
    if (System.currentTimeMillis() == 0) {
      missingClass.instanceMethod();
      return "foo";
    }
    return "bar";
  }

  public static String run14(MissingClass missingClass) {
    if (System.currentTimeMillis() == 0) {
      missingClass.instanceMethod();
      return "foo";
    }
    return "bar";
  }

  public static String run15(MissingClass missingClass) {
    if (System.currentTimeMillis() == 0) {
      missingClass.instanceMethod();
      return "foo";
    }
    return "bar";
  }

  public static String run16(MissingClass missingClass) {
    if (System.currentTimeMillis() == 0) {
      missingClass.instanceMethod();
      return "foo";
    }
    return "bar";
  }

  public static String run17(MissingClass missingClass) {
    if (System.currentTimeMillis() == 0) {
      missingClass.instanceMethod();
      return "foo";
    }
    return "bar";
  }

  public static String run18(MissingClass missingClass) {
    if (System.currentTimeMillis() == 0) {
      missingClass.instanceMethod();
      return "foo";
    }
    return "bar";
  }

  public static String run19(MissingClass missingClass) {
    if (System.currentTimeMillis() == 0) {
      missingClass.instanceMethod();
      return "foo";
    }
    return "bar";
  }

  public static String run20(MissingClass missingClass) {
    if (System.currentTimeMillis() == 0) {
      missingClass.instanceMethod();
      return "foo";
    }
    return "bar";
  }
}
