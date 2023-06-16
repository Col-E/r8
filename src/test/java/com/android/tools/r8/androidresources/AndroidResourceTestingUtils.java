// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import java.nio.charset.StandardCharsets;

public class AndroidResourceTestingUtils {

  // Taken from default empty android studio activity template
  public static String TEST_MANIFEST =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
          + "    xmlns:tools=\"http://schemas.android.com/tools\">\n"
          + "\n"
          + "    <application\n"
          + "        android:allowBackup=\"true\"\n"
          + "        android:dataExtractionRules=\"@xml/data_extraction_rules\"\n"
          + "        android:fullBackupContent=\"@xml/backup_rules\"\n"
          + "        android:icon=\"@mipmap/ic_launcher\"\n"
          + "        android:label=\"@string/app_name\"\n"
          + "        android:roundIcon=\"@mipmap/ic_launcher_round\"\n"
          + "        android:supportsRtl=\"true\"\n"
          + "        android:theme=\"@style/Theme.MyApplication\"\n"
          + "        tools:targetApi=\"31\">\n"
          + "        <activity\n"
          + "            android:name=\".MainActivity\"\n"
          + "            android:exported=\"true\"\n"
          + "            android:label=\"@string/app_name\"\n"
          + "            android:theme=\"@style/Theme.MyApplication.NoActionBar\">\n"
          + "            <intent-filter>\n"
          + "                <action android:name=\"android.intent.action.MAIN\" />\n"
          + "\n"
          + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
          + "            </intent-filter>\n"
          + "\n"
          + "            <meta-data\n"
          + "                android:name=\"android.app.lib_name\"\n"
          + "                android:value=\"\" />\n"
          + "        </activity>\n"
          + "    </application>\n"
          + "\n"
          + "</manifest>";

  // TODO(287399385): Add testing utils for generating/consuming resource tables.
  public static byte[] TEST_RESOURCE_TABLE = "RESOURCE_TABLE".getBytes(StandardCharsets.UTF_8);

  // The below byte arrays are lifted from the resource shrinkers DummyContent

  // A 1x1 pixel PNG of type BufferedImage.TYPE_BYTE_GRAY
  public static final byte[] TINY_PNG =
      new byte[] {
        (byte) -119, (byte) 80, (byte) 78, (byte) 71, (byte) 13, (byte) 10,
        (byte) 26, (byte) 10, (byte) 0, (byte) 0, (byte) 0, (byte) 13,
        (byte) 73, (byte) 72, (byte) 68, (byte) 82, (byte) 0, (byte) 0,
        (byte) 0, (byte) 1, (byte) 0, (byte) 0, (byte) 0, (byte) 1,
        (byte) 8, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 58,
        (byte) 126, (byte) -101, (byte) 85, (byte) 0, (byte) 0, (byte) 0,
        (byte) 10, (byte) 73, (byte) 68, (byte) 65, (byte) 84, (byte) 120,
        (byte) -38, (byte) 99, (byte) 96, (byte) 0, (byte) 0, (byte) 0,
        (byte) 2, (byte) 0, (byte) 1, (byte) -27, (byte) 39, (byte) -34,
        (byte) -4, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 73,
        (byte) 69, (byte) 78, (byte) 68, (byte) -82, (byte) 66, (byte) 96,
        (byte) -126
      };

  // The XML document <x/> as a proto packed with AAPT2
  public static final byte[] TINY_PROTO_XML =
      new byte[] {0xa, 0x3, 0x1a, 0x1, 0x78, 0x1a, 0x2, 0x8, 0x1};
}
