// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import static com.android.tools.r8.utils.DescriptorUtils.getPathFromDescriptor;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import jasmin.ClassFile;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class JasminBuilder {

  public enum ClassFileVersion {
    JDK_1_1 {
      @Override
      public int getMajorVersion() {
        return 45;
      }

      @Override
      public int getMinorVersion() {
        return 3;
      }
    },
    JDK_1_2 {
      @Override
      public int getMajorVersion() {
        return 46;
      }
    },
    JDK_1_3 {
      @Override
      public int getMajorVersion() {
        return 47;
      }
    },
    JDK_1_4 {
      @Override
      public int getMajorVersion() {
        return 48;
      }
    },
    /** JSE 5 is not fully supported by Jasmin. Interfaces will not work. */
    JSE_5 {
      @Override
      public int getMajorVersion() {
        return 49;
      }
    };

    public abstract int getMajorVersion();

    public int getMinorVersion() {
      return 0;
    }
  }

  public class ClassBuilder {
    public final String name;
    public final String superName;
    public final ImmutableList<String> interfaces;
    private final List<String> methods = new ArrayList<>();
    private final List<String> fields = new ArrayList<>();
    private boolean makeInit = false;
    private boolean hasInit = false;
    private final List<String> clinit = new ArrayList<>();
    private boolean isAbstract = false;
    private boolean isInterface = false;
    private String access = "public";

    private ClassBuilder(String name) {
      this(name, "java/lang/Object");
    }

    private ClassBuilder(String name, String superName) {
      this.name = name;
      this.superName = superName;
      this.interfaces = ImmutableList.of();
    }

    private ClassBuilder(String name, String superName, String... interfaces) {
      this.name = name;
      this.superName = superName;
      this.interfaces = ImmutableList.copyOf(interfaces);
    }

    public String getSourceFile() {
      return name + ".j";
    }

    public String getDescriptor() {
      return "L" + name + ";";
    }

    public MethodSignature addAbstractMethod(
        String name,
        List<String> argumentTypes,
        String returnType) {
      return addMethod("public abstract", name, argumentTypes, returnType);
    }

    public MethodSignature addFinalMethod(
        String name,
        List<String> argumentTypes,
        String returnType,
        String... lines) {
      makeInit = true;
      return addMethod("public final", name, argumentTypes, returnType, lines);
    }

    public MethodSignature addVirtualMethod(String name, String returnType, String... lines) {
      return addVirtualMethod(name, ImmutableList.of(), returnType, lines);
    }

    public MethodSignature addVirtualMethod(
        String name,
        List<String> argumentTypes,
        String returnType,
        String... lines) {
      makeInit = true;
      return addMethod("public", name, argumentTypes, returnType, lines);
    }

    /**
     * Note that the JVM rejects native methods with code. This method is used to test that D8
     * removes code from native methods.
     */
    public MethodSignature addNativeMethodWithCode(
        String name,
        List<String> argumentTypes,
        String returnType,
        String... lines) {
      makeInit = true;
      return addMethod("public static native", name, argumentTypes, returnType, lines);
    }

    public MethodSignature addBridgeMethod(
        String name,
        List<String> argumentTypes,
        String returnType,
        String... lines) {
      makeInit = true;
      return addMethod("public bridge", name, argumentTypes, returnType, lines);
    }

    public MethodSignature addPrivateVirtualMethod(
        String name,
        List<String> argumentTypes,
        String returnType,
        String... lines) {
      makeInit = true;
      return addMethod("private", name, argumentTypes, returnType, lines);
    }

    public MethodSignature addStaticMethod(
        String name,
        List<String> argumentTypes,
        String returnType,
        String... lines) {
      return addMethod("public static", name, argumentTypes, returnType, lines);
    }

    public MethodSignature addPackagePrivateStaticMethod(
        String name,
        List<String> argumentTypes,
        String returnType,
        String... lines) {
      return addMethod("static", name, argumentTypes, returnType, lines);
    }

    public MethodSignature addMainMethod(Iterable<String> lines) {
      return addMainMethod(Iterables.toArray(lines, String.class));
    }

    public MethodSignature addMainMethod(String... lines) {
      return addStaticMethod("main", ImmutableList.of("[Ljava/lang/String;"), "V", lines);
    }

    public MethodSignature addMethod(
        String access,
        String name,
        List<String> argumentTypes,
        String returnType,
        String... lines) {
      StringBuilder builder = new StringBuilder();
      builder.append(".method ").append(access).append(" ").append(name)
          .append(StringUtils.join(argumentTypes, "", BraceType.PARENS))
          .append(returnType).append("\n");
      for (String line : lines) {
        builder.append(line).append("\n");
      }
      builder.append(".end method\n");
      methods.add(builder.toString());

      String returnJavaType = DescriptorUtils.descriptorToJavaType(returnType);
      String[] argumentJavaTypes = new String[argumentTypes.size()];
      for (int i = 0; i < argumentTypes.size(); i++) {
        argumentJavaTypes[i] = DescriptorUtils.descriptorToJavaType(argumentTypes.get(i));
      }
      return new MethodSignature(name, returnJavaType, argumentJavaTypes);
    }

    public void addClassInitializer(String... lines) {
      clinit.addAll(Arrays.asList(lines));
    }

    public FieldSignature addField(String flags, String name, String type, String value) {
      fields.add(
          ".field " + flags + " " + name + " " + type + (value != null ? (" = " + value) : ""));
      return new FieldSignature(name, type);
    }

    public FieldSignature addStaticField(String name, String type, String value) {
      return addField("public static", name, type, value);
    }

    public FieldSignature addStaticFinalField(String name, String type, String value) {
      return addField("public static final", name, type, value);
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append(".bytecode ").append(majorVersion).append('.').append(minorVersion)
          .append('\n');
      builder.append(".source ").append(getSourceFile()).append('\n');
      builder.append(".class");
      if (isAbstract) {
        builder.append(" abstract");
      } else if (isInterface) {
        builder.append(" interface abstract");
      }
      builder.append(" ").append(access).append(" ").append(name).append('\n');
      builder.append(".super ").append(superName).append('\n');
      for (String iface : interfaces) {
        builder.append(".implements ").append(iface).append('\n');
      }
      if (makeInit && !hasInit) {
        builder
            .append(".method public <init>()V\n")
            .append(".limit locals 1\n")
            .append(".limit stack 1\n")
            .append("  aload 0\n")
            .append("  invokespecial ").append(superName).append("/<init>()V\n")
            .append("  return\n")
            .append(".end method\n");
      }
      for (String field : fields) {
        builder.append(field).append("\n");
      }
      for (String method : methods) {
        builder.append(method).append("\n");
      }
      if (!clinit.isEmpty()) {
        builder.append(".method public static <clinit>()V\n");
        clinit.forEach(line -> builder.append(line).append('\n'));
        builder.append(".end method\n");
      }
      return builder.toString();
    }

    public void setIsAbstract() {
      isAbstract = true;
    }

    public void setIsInterface() {
      isInterface = true;
    }

    public void setAccess(String access) {
      this.access = access;
    }

    public MethodSignature addDefaultConstructor() {
      assert !hasInit;
      hasInit = true;
      return addMethod("public", "<init>", Collections.emptyList(), "V",
          ".limit stack 1",
          ".limit locals 1",
          "  aload_0",
          "  invokenonvirtual " + superName + "/<init>()V",
          "  return");
    }
  }

  private final List<ClassBuilder> classes = new ArrayList<>();
  private final int minorVersion;
  private final int majorVersion;

  public JasminBuilder() {
    this(ClassFileVersion.JDK_1_4);
  }

  public JasminBuilder(ClassFileVersion version) {
    majorVersion = version.getMajorVersion();
    minorVersion = version.getMinorVersion();
  }

  public ClassBuilder addClass(String name) {
    ClassBuilder builder = new ClassBuilder(name);
    classes.add(builder);
    return builder;
  }

  public ClassBuilder addClass(String name, String superName) {
    ClassBuilder builder = new ClassBuilder(name, superName);
    classes.add(builder);
    return builder;
  }

  public ClassBuilder addClass(String name, String superName, String... interfaces) {
    ClassBuilder builder = new ClassBuilder(name, superName, interfaces);
    classes.add(builder);
    return builder;
  }

  public ClassBuilder addInterface(String name, String... interfaces) {
    // Interfaces are broken in Jasmin (the ACC_SUPER access flag is set) and the JSE_5 and later
    // will not load corresponding classes.
    assert majorVersion <= ClassFileVersion.JDK_1_4.getMajorVersion();
    ClassBuilder builder = new ClassBuilder(name, "java/lang/Object", interfaces);
    builder.setIsInterface();
    classes.add(builder);
    return builder;
  }

  public ImmutableList<ClassBuilder> getClasses() {
    return ImmutableList.copyOf(classes);
  }

  private static byte[] compile(ClassBuilder builder) throws Exception {
    ClassFile file = new ClassFile();
    file.readJasmin(new StringReader(builder.toString()), builder.name, false);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    file.write(out);
    return out.toByteArray();
  }

  public ImmutableList.Builder<byte[]> buildClasses(ImmutableList.Builder<byte[]> builder)
      throws Exception {
    for (ClassBuilder clazz : classes) {
      builder.add(compile(clazz));
    }
    return builder;
  }

  public List<byte[]> buildClasses() throws Exception {
    return buildClasses(ImmutableList.builder()).build();
  }

  public AndroidApp build() throws Exception {
    Origin root = new PathOrigin(Paths.get("JasminBuilder"));
    AndroidApp.Builder builder = AndroidApp.builder();
    for (ClassBuilder clazz : classes) {
      Origin origin = new Origin(root) {
        @Override
        public String part() {
          return clazz.getSourceFile();
        }
      };
      builder.addClassProgramData(
          compile(clazz), origin, Collections.singleton(clazz.getDescriptor()));
    }
    return builder.build();
  }

  public List<Path> writeClassFiles(Path output) throws Exception {
    List<Path> outputs = new ArrayList<>(classes.size());
    for (ClassBuilder clazz : classes) {
      Path path = output.resolve(getPathFromDescriptor(clazz.getDescriptor()));
      Files.createDirectories(path.getParent());
      Files.write(path, compile(clazz));
      outputs.add(path);
    }
    return outputs;
  }

  public void writeClassFiles(ClassFileConsumer consumer, DiagnosticsHandler handler)
      throws Exception {
    for (ClassBuilder clazz : classes) {
      consumer.accept(ByteDataView.of(compile(clazz)), clazz.getDescriptor(), handler);
    }
  }

  public void writeJar(Path output, DiagnosticsHandler handler) throws Exception {
    ClassFileConsumer consumer = new ClassFileConsumer.ArchiveConsumer(output);
    writeClassFiles(consumer, handler);
    consumer.finished(handler);
  }

  public DexApplication read() throws Exception {
    return read(new InternalOptions());
  }

  public DexApplication read(InternalOptions options) throws Exception {
    Timing timing = new Timing("JasminTest");
    return new ApplicationReader(build(), options, timing).read();
  }
}
