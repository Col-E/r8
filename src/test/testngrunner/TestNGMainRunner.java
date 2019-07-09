// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// This class is kept in a separate package because of the org.testng dependency.
// R8 tests do not depend on testng, this class does.
public class TestNGMainRunner {

  private static void runTestNg(Class<?> testClass, int verbose) {
    System.out.println("Running tests in " + testClass.getName());
    org.testng.TestNG testng = new org.testng.TestNG(false);
    testng.setTestClasses(new Class<?>[] {testClass});
    testng.setVerbose(verbose);
    // Deprecated API used because it works on Android unlike the recommended one.
    testng.addListener(new org.testng.reporters.TextReporter(testClass.getName(), verbose));
    try {
      testng.run();
      System.out.print("Tests result in " + testClass.getName() + ": ");
      if (testng.hasFailure()) {
        System.out.println("FAILURE");
      } else {
        System.out.println("SUCCESS");
      }
    } catch (RuntimeException | Error e) {
      System.out.print("Tests result in " + testClass.getName() + ": ");
      System.out.println("ERROR");
      e.printStackTrace();
    }
  }

  public static void main(String[] args) throws Exception {
    // First arg is the verbosity level.
    // Second arg is the class to run.
    int verbose = Integer.parseInt(args[0]);
    runTestNg(Class.forName(args[1]), verbose);
  }
}
