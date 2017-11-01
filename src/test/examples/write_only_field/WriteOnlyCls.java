// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package write_only_field;

public class WriteOnlyCls {

  static class DataObj {
    final int f1;
    DataObj(int f1) {
      this.f1 = f1;
    }
  }

  static DataObj static_field = new DataObj(1);

  DataObj instance_field;

  public WriteOnlyCls(int n) {
    instance_field = new DataObj(n);
  }

  public static void main(String[] args) {
    WriteOnlyCls instance = new WriteOnlyCls(2);
  }

}
