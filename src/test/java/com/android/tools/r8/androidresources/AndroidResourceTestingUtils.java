// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.MoreCollectors;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import org.junit.rules.TemporaryFolder;

public class AndroidResourceTestingUtils {

  public static String SIMPLE_MANIFEST_WITH_STRING =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
          + "          package=\"com.android.tools.r8\">\n"
          + "    <application android:label=\"@string/app_name\">\n"
          + "    </application>\n"
          + "</manifest>\n"
          + "\n";

  public static class AndroidTestRClass {
    private final Path javaFilePath;
    private final Path rootDirectory;

    AndroidTestRClass(Path rootDirectory) throws IOException {
      this.rootDirectory = rootDirectory;
      this.javaFilePath =
          Files.walk(rootDirectory)
              .filter(path -> path.endsWith("R.java"))
              .collect(MoreCollectors.onlyElement());
    }

    public Path getJavaFilePath() {
      return javaFilePath;
    }

    public Path getRootDirectory() {
      return rootDirectory;
    }
  }

  public static class AndroidTestResource {
    private final AndroidTestRClass rClass;
    private final Path resourceZip;

    AndroidTestResource(AndroidTestRClass rClass, Path resourceZip) {
      this.rClass = rClass;
      this.resourceZip = resourceZip;
    }

    public AndroidTestRClass getRClass() {
      return rClass;
    }

    public Path getResourceZip() {
      return resourceZip;
    }
  }

  public static class AndroidTestResourceBuilder {
    private String manifest;
    private Map<String, String> stringValues = new TreeMap<>();
    private Map<String, byte[]> drawables = new TreeMap<>();

    AndroidTestResourceBuilder withManifest(String manifest) {
      this.manifest = manifest;
      return this;
    }

    AndroidTestResourceBuilder withSimpleManifest() {
      this.manifest = SIMPLE_MANIFEST_WITH_STRING;
      return this;
    }

    AndroidTestResourceBuilder addStringValue(String name, String value) {
      stringValues.put(name, value);
      return this;
    }

    AndroidTestResourceBuilder addDrawable(String name, byte[] value) {
      drawables.put(name, value);
      return this;
    }

    AndroidTestResource build(TemporaryFolder temp) throws IOException {
      Path manifestPath =
          FileUtils.writeTextFile(temp.newFile("AndroidManifest.xml").toPath(), this.manifest);
      Path resFolder = temp.newFolder("res").toPath();
      if (stringValues.size() > 0) {
        FileUtils.writeTextFile(
            temp.newFolder("res", "values").toPath().resolve("strings.xml"),
            createStringResourceXml());
      }
      if (drawables.size() > 0) {
        for (Entry<String, byte[]> entry : drawables.entrySet()) {
          FileUtils.writeToFile(
              temp.newFolder("res", "drawable").toPath().resolve(entry.getKey()),
              null,
              entry.getValue());
        }
      }

      Path output = temp.newFile("resources.zip").toPath();
      Path rClassOutput = temp.newFolder("aapt_R_class").toPath();
      compileWithAapt2(resFolder, manifestPath, rClassOutput, output, temp);
      return new AndroidTestResource(new AndroidTestRClass(rClassOutput), output);
    }

    private String createStringResourceXml() {
      StringBuilder stringBuilder = new StringBuilder("<resources>\n");
      stringValues.forEach(
          (name, value) ->
              stringBuilder.append("<string name=\"" + name + "\">" + value + "</string>\n"));
      stringBuilder.append("</resources>");
      return stringBuilder.toString();
    }
  }

  public static void compileWithAapt2(
      Path resFolder, Path manifest, Path rClassFolder, Path resourceZip, TemporaryFolder temp)
      throws IOException {
    Path compileOutput = temp.newFile("compiled.zip").toPath();
    ProcessResult compileProcessResult =
        ToolHelper.runAapt2(
            "compile", "-o", compileOutput.toString(), "--dir", resFolder.toString());
    failOnError(compileProcessResult);

    ProcessResult linkProcesResult =
        ToolHelper.runAapt2(
            "link",
            "-I",
            ToolHelper.getAndroidJar(AndroidApiLevel.S).toString(),
            "-o",
            resourceZip.toString(),
            "--java",
            rClassFolder.toString(),
            "--manifest",
            manifest.toString(),
            "--proto-format",
            compileOutput.toString());
    failOnError(linkProcesResult);
  }

  private static void failOnError(ProcessResult processResult) {
    if (processResult.exitCode != 0) {
      throw new RuntimeException("Failed aapt2: \n" + processResult);
    }
  }

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
