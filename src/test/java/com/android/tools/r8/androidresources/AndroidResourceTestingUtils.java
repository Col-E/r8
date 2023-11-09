// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidresources;

import static com.android.tools.r8.TestBase.javac;
import static com.android.tools.r8.TestBase.transformer;

import com.android.aapt.Resources;
import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.ResourceTable;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.transformers.ClassTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StreamUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.MoreCollectors;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.rules.TemporaryFolder;

public class AndroidResourceTestingUtils {

  enum RClassType {
    STRING,
    DRAWABLE,
    XML;

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

  public static String XML_FILE_WITH_STRING_REFERENCE =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<paths>\n"
          + "    <files-path\n"
          + "        name=\"@string/%s\"\n"
          + "        path=\"let/it/be\" />\n"
          + "</paths>";

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

  // Easy traversable resource table.
  public static class TestResourceTable {
    private Map<String, ResourceNameToValueMapping> mapping = new HashMap<>();

    private TestResourceTable(ResourceTable resourceTable) {
      // For now, we don't have any test that use multiple packages.
      assert resourceTable.getPackageCount() == 1;
      for (Resources.Type type : resourceTable.getPackage(0).getTypeList()) {
        String typeName = type.getName();
        mapping.put(typeName, new ResourceNameToValueMapping(type));
      }
    }

    public static TestResourceTable parseFrom(byte[] bytes) throws InvalidProtocolBufferException {
      return new TestResourceTable(ResourceTable.parseFrom(bytes));
    }

    public boolean containsValueFor(String type, String name) {
      return mapping.containsKey(type) && mapping.get(type).containsValueFor(name);
    }

    public Collection<String> entriesForType(String type) {
      return mapping.get(type).mapping.keySet();
    }

    public static class ResourceNameToValueMapping {
      private final Map<String, List<ResourceValue>> mapping = new HashMap<>();

      public ResourceNameToValueMapping(Resources.Type type) {
        for (Resources.Entry entry : type.getEntryList()) {
          String name = entry.getName();
          List<ResourceValue> entries = new ArrayList<>();
          for (ConfigValue configValue : entry.getConfigValueList()) {
            Item item = configValue.getValue().getItem();
            // Currently supporting files and strings, we just flatten this to strings for easy
            // testing.
            if (item.hasFile()) {
              entries.add(
                  new ResourceValue(item.getFile().getPath(), configValue.getConfig().toString()));
            } else if (item.hasStr()) {
              entries.add(
                  new ResourceValue(item.getStr().getValue(), configValue.getConfig().toString()));
            }
            mapping.put(name, entries);
          }
        }
      }

      public boolean containsValueFor(String name) {
        return mapping.containsKey(name);
      }

      public static class ResourceValue {

        private final String value;
        private final String config;

        public ResourceValue(String value, String config) {
          this.value = value;
          this.config = config;
        }

        public String getValue() {
          return value;
        }

        public String getConfig() {
          return config;
        }
      }
    }
  }

  public static class ResourceTableInspector {

    private final TestResourceTable testResourceTable;

    public ResourceTableInspector(byte[] bytes) throws InvalidProtocolBufferException {
      testResourceTable = TestResourceTable.parseFrom(bytes);
    }

    public void assertContainsResourceWithName(String type, String name) {
      Assert.assertTrue(
          StringUtils.join(",", entries(type)), testResourceTable.containsValueFor(type, name));
    }

    public void assertDoesNotContainResourceWithName(String type, String name) {
      Assert.assertFalse(
          StringUtils.join(",", entries(type)), testResourceTable.containsValueFor(type, name));
    }

    public Collection<String> entries(String type) {
      return testResourceTable.entriesForType(type);
    }
  }

  public static class AndroidTestResourceBuilder {
    private String manifest;
    private final Map<String, String> stringValues = new TreeMap<>();
    private final Map<String, byte[]> drawables = new TreeMap<>();
    private final Map<String, String> xmlFiles = new TreeMap<>();
    private final List<Class<?>> classesToRemap = new ArrayList<>();
    private int packageId = 0x7f;

    // Create the android resources from the passed in R classes
    // All values will be generated based on the fields in the class.
    // This takes the actual inner classes (e.g., R$String)
    // These R classes will be used to rewrite the namespace and class names on the aapt2
    // generated names.
    AndroidTestResourceBuilder addRClassInitializeWithDefaultValues(Class<?>... rClasses) {
      for (Class<?> rClass : rClasses) {
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

    AndroidTestResourceBuilder addXmlWithStringReference(
        String xmlName, String nameOfReferencedString) {
      xmlFiles.put(xmlName, String.format(XML_FILE_WITH_STRING_REFERENCE, nameOfReferencedString));
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

    AndroidTestResourceBuilder setPackageId(int packageId) {
      this.packageId = packageId;
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
        File drawableFolder = temp.newFolder("res", "drawable");
        for (Entry<String, byte[]> entry : drawables.entrySet()) {
          FileUtils.writeToFile(
              drawableFolder.toPath().resolve(entry.getKey()), null, entry.getValue());
        }
      }
      if (xmlFiles.size() > 0) {
        File xmlFolder = temp.newFolder("res", "xml");
        for (Entry<String, String> entry : xmlFiles.entrySet()) {
          FileUtils.writeTextFile(xmlFolder.toPath().resolve(entry.getKey()), entry.getValue());
        }
      }

      Path output = temp.newFile("resources.zip").toPath();
      Path rClassOutputDir = temp.newFolder("aapt_R_class").toPath();
      compileWithAapt2(resFolder, manifestPath, rClassOutputDir, output, temp, packageId);
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
            if (ZipUtils.isClassFile(entry.getName()) && !entry.getName().endsWith("R.class")) {
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

                            @Override
                            public void visitNestHost(String nestHost) {
                              // Don't make nest host relationsships
                            }

                            @Override
                            public void visitOuterClass(
                                String owner, String name, String descriptor) {
                              // Don't make the inner<>outer class connection
                            }

                            @Override
                            public void visitInnerClass(
                                String name, String outerName, String innerName, int access) {
                              // Don't make the inner<>outer class connection
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
      Path resFolder,
      Path manifest,
      Path rClassFolder,
      Path resourceZip,
      TemporaryFolder temp,
      int packageId)
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
            "--package-id",
            "" + packageId,
            "--allow-reserved-package-id",
            "--rename-resources-package",
            "thepackage" + packageId + ".foobar",
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
