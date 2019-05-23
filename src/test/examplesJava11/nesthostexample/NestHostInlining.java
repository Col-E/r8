// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package nesthostexample;

public class NestHostInlining {

  private String field = "inlining";

  public static class InnerWithPrivAccess {
    public String access(NestHostInlining host) {
      return host.field;
    }
  }

  public static class InnerNoPrivAccess {
    public String print() {
      return "InnerNoPrivAccess";
    }
  }

  public abstract static class EmptyNoPrivAccess {}

  public abstract static class EmptyWithPrivAccess {
    public String access(NestHostInlining host) {
      return host.field;
    }
  }

  public static void main(String[] args) {
    System.out.println(new InnerWithPrivAccess().access(new NestHostInlining()));
    System.out.println(new InnerNoPrivAccess().print());
  }
}
