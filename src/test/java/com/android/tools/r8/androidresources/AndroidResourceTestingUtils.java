// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static com.android.tools.r8.TestBase.javac;
import static com.android.tools.r8.TestBase.transformer;

import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StreamUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.MoreCollectors;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.rules.TemporaryFolder;

public class AndroidResourceTestingUtils {

  enum RClassType {
    STRING,
    DRAWABLE;

    public static RClassType fromClass(Class clazz) {
      String type = rClassWithoutNamespaceAndOuter(clazz).substring(2);
      return RClassType.valueOf(type.toUpperCase());
    }
  }

  private static String rClassWithoutNamespaceAndOuter(Class clazz) {
    return rClassWithoutNamespaceAndOuter(clazz.getName());
  }

  private static String rClassWithoutNamespaceAndOuter(String name) {
    assert isInnerRClass(name);
    int dollarIndex = name.lastIndexOf("$");
    String specificRClass = name.substring(dollarIndex - 1);
    return specificRClass;
  }

  private static boolean isInnerRClass(String name) {
    int dollarIndex = name.lastIndexOf("$");
    return dollarIndex > 0 && name.charAt(dollarIndex - 1) == 'R';
  }

  public static String SIMPLE_MANIFEST_WITH_APP_NAME =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
          + "          package=\"com.android.tools.r8\">\n"
          + "    <application android:label=\"@string/app_name\">\n"
          + "    </application>\n"
          + "</manifest>\n"
          + "\n";

  public static class AndroidTestRClass {
    // The original aapt2 generated R.java class
    private final Path javaFilePath;
    // The compiled class files, with the class names rewritten to the names used in passed
    // in R class from the test.
    private final List<byte[]> classFileData;

    AndroidTestRClass(Path javaFilePath, List<byte[]> classFileData) throws IOException {
      this.javaFilePath = javaFilePath;
      this.classFileData = classFileData;
    }
    public Path getJavaFilePath() {
      return javaFilePath;
    }

    public List<byte[]> getClassFileData() {
      return classFileData;
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
    private List<Class> classesToRemap = new ArrayList();

    // Create the android resources from the passed in R classes
    // All values will be generated based on the fields in the class.
    // This takes the actual inner classes (e.g., R$String)
    // These R classes will be used to rewrite the namespace and class names on the aapt2
    // generated names.
    AndroidTestResourceBuilder addRClassInitializeWithDefaultValues(Class... rClasses) {
      for (Class rClass : rClasses) {
        classesToRemap.add(rClass);
        RClassType rClassType = RClassType.fromClass(rClass);
        for (Field declaredField : rClass.getDeclaredFields()) {
          String name = declaredField.getName();
          if (rClassType == RClassType.STRING) {
            addStringValue(name, name);
          }
          if (rClassType == RClassType.DRAWABLE) {
            addDrawable(name, TINY_PNG);
          }
        }
      }
      return this;
    }

    AndroidTestResourceBuilder withManifest(String manifest) {
      this.manifest = manifest;
      return this;
    }

    AndroidTestResourceBuilder withSimpleManifestAndAppNameString() {
      this.manifest = SIMPLE_MANIFEST_WITH_APP_NAME;
      addStringValue("app_name", "Most important app ever.");
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
      Path rClassOutputDir = temp.newFolder("aapt_R_class").toPath();
      compileWithAapt2(resFolder, manifestPath, rClassOutputDir, output, temp);
      Path rClassJavaFile =
          Files.walk(rClassOutputDir)
              .filter(path -> path.endsWith("R.java"))
              .collect(MoreCollectors.onlyElement());
      Path rClassClassFileOutput =
          javac(CfRuntime.getDefaultCfRuntime(), temp).addSourceFiles(rClassJavaFile).compile();
      Map<String, String> noNamespaceToProgramMap =
          classesToRemap.stream()
              .collect(
                  Collectors.toMap(
                      AndroidResourceTestingUtils::rClassWithoutNamespaceAndOuter,
                      DescriptorUtils::getClassBinaryName));
      List<byte[]> rewrittenRClassFiles = new ArrayList<>();
      ZipUtils.iter(
          rClassClassFileOutput,
          (entry, input) -> {
            if (ZipUtils.isClassFile(entry.getName())) {
              rewrittenRClassFiles.add(
                  transformer(StreamUtils.streamToByteArrayClose(input), null)
                      .addClassTransformer(
                          new ClassTransformer() {
                            @Override
                            public void visit(
                                int version,
                                int access,
                                String name,
                                String signature,
                                String superName,
                                String[] interfaces) {
                              String maybeTransformedName =
                                  isInnerRClass(name)
                                      ? noNamespaceToProgramMap.getOrDefault(
                                          rClassWithoutNamespaceAndOuter(name), name)
                                      : name;
                              super.visit(
                                  version,
                                  access,
                                  maybeTransformedName,
                                  signature,
                                  superName,
                                  interfaces);
                            }
                          })
                      .transform());
            }
          });
      return new AndroidTestResource(
          new AndroidTestRClass(rClassJavaFile, rewrittenRClassFiles), output);
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
