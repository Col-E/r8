// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package softverificationerror;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class ApiCallerInlined {

  public static void callApi(android.content.Context context) {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= 26) {
      constructUnknownObjectAndCallUnknownMethod(context);
    }
  }

  public static void constructUnknownObject(android.content.Context context) {
    NotificationChannel channel =
        new NotificationChannel("CHANNEL_ID", "FOO", NotificationManager.IMPORTANCE_DEFAULT);
    channel.setDescription("This is a test channel");
  }

  public static void callUnknownMethod(android.content.Context context) {
    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(NotificationManager.class);
    notificationManager.createNotificationChannel(null);
  }

  public static void constructUnknownObjectAndCallUnknownMethod(android.content.Context context) {
    NotificationChannel channel =
        new NotificationChannel("CHANNEL_ID", "FOO", NotificationManager.IMPORTANCE_DEFAULT);
    channel.setDescription("This is a test channel");
    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(NotificationManager.class);
    notificationManager.createNotificationChannel(channel);
  }
}
