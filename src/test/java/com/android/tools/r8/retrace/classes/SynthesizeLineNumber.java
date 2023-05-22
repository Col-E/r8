// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.classes;

import com.android.tools.r8.NeverInline;

public class SynthesizeLineNumber {

  public static class A {
    @NeverInline
    public static void foo() throws Exception {
      throw new Exception("A::foo");
    }
  }

  public static class B {
    @NeverInline
    public static void bar() throws Exception {
      throw new Exception("B::bar");
    }
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      call(System.currentTimeMillis() > 0 ? 0 : 1, args[0].equals("synthesize"));
    }

    public static void call(int classId, boolean shouldSynthesizeLineNumber) throws Exception {
      try {
        if (classId == 0) {
          A.foo();
        } else {
          B.bar();
        }
      } catch (Exception e) {
        if (shouldSynthesizeLineNumber) {
          Exception exception = new Exception(e.getMessage(), e.getCause());
          StackTraceElement[] stackTrace = e.getStackTrace();
          // The obfuscated class name after repackaging and class name minification.
          String className = "com.android.tools.r8.retrace.classes.SynthesizeLineNumber$Main";
          // The obfuscated method name after minification. Since this is inlined into main it is
          // just main.
          String obfuscatedMethodName = "main";
          for (int i = 0; i < stackTrace.length; i++) {
            StackTraceElement original = stackTrace[i];
            // The line number range will need to be the range of the try block for catching the
            // exception.
            if (original.getClassName().equals(className)
                && original.getMethodName().equals(obfuscatedMethodName)
                && original.getLineNumber() >= 0
                && original.getLineNumber() <= 10) {
              stackTrace[i] =
                  new StackTraceElement(
                      original.getClassName(),
                      original.getMethodName(),
                      original.getFileName(),
                      classId + 1000);
              break;
            }
          }
          exception.setStackTrace(stackTrace);
          throw exception;
        }
        throw e;
      }
    }
  }
}
