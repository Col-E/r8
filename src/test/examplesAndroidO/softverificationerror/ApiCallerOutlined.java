// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package softverificationerror;

import android.os.Build;

public class ApiCallerOutlined {

  public static void callApi(android.content.Context context) {
    if (Build.VERSION.SDK_INT >= 26) {
      ApiCallerInlined.constructUnknownObjectAndCallUnknownMethod(context);
    }
  }
}
