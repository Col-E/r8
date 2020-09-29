// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package nesthostexample;

import com.android.tools.r8.NeverInline;

public class NestHostInliningSubclasses {

  public static class InnerWithPrivAccess extends NestHostInlining.InnerWithPrivAccess {
    public String accessSubclass(NestHostInlining host) {
      return super.access(host) + "Subclass";
    }
  }

  public static class InnerNoPrivAccess extends NestHostInlining.InnerNoPrivAccess {
    @NeverInline
    public String printSubclass() {
      return super.print() + "Subclass";
    }
  }

  public static void main(String[] args) {
    System.out.println(new InnerWithPrivAccess().accessSubclass(new NestHostInlining()));
    System.out.println(new InnerNoPrivAccess().printSubclass());
  }
}
