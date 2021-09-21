// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

public class RecordMain {

  record MainRecord(String data) {}
  ;

  public static void main(String[] args) {
    System.out.println(new MainRecord("main") instanceof java.lang.Record);
    System.out.println(RecordLib.getRecord() instanceof java.lang.Record);
  }
}
