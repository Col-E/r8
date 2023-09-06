// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package softverificationerror;

public class Main {

  public static void test(android.content.Context context) {
    ApiCallerInlined.callApi(context);
    ApiCallerOutlined.callApi(context);
  }
}
