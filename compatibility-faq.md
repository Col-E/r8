# R8 compatibility FAQ

R8 uses the same configuration specification language as ProGuard, and tries to
be compatible with ProGuard. However as R8 has different optimizations it can be
necessary to change the configuration when switching to R8.

This FAQ collects some of the common issues.

## GSON

### Member in a data object is always `null`

For data classes used for serialization all fields that are used in the
serialization must be kept by the configuration. R8 can decide to replace
instances of types that are never instantiated with `null`. So if instances of a
given class are only created through deserialization from JSON, R8 will not see
that class as instantiated leaving it as always `null`.

If the `@SerializedName` annotation is used consistently for data classes the
following keep rule can be used:

```
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
```

This will ensure that all fields annotated with `SerializedName` will be
kept. These fields can still be renamed during obfuscation as the
`SerializedName` annotation (not the source field name) controls the name in the
JSON serialization.

If the `@SerializedName` annotation is _not_ used the following conservative
rule can be used for each data class:

```
-keepclassmembers class MyDataClass {
  !transient <fields>;
}
```

This will ensure that all fields are kept and not renamed for these
classes. Fields with modifier `transient` are never serialized and therefore
keeping these is not needed.

### Error `java.lang.IllegalArgumentException: class <class name> declares multiple JSON fields named <name>`

This can be caused by obfuscation selecting the same name for private fields in
several classes in a class hierachy. Consider the following example:

```
class A {
  private String fieldInA;
}

class B extends A {
  private String fieldInB;
}
```

Here R8 can choose to rename both `fieldInA` and `fieldInB` to the same name,
e.g. `a`. This creates a conflict when GSON is used to either serialize an
instance of class `B` to JSON or create an instance of class `B` from JSON. If
the fields should _not_ be serialized they should be marked `transient` so that
they will be ignored by GSON:

```
class A {
  private transient String fieldInA;
}

class B extends A {
  private transient String fieldInB;
}
```

If the fields _are_ to be serialized, the annotation `SerializedName` can be
used to fix the `IllegalArgumentException` together the rule to keep fields
annotated with `SerializedName`

```
class A {
  @SerializedName("fieldInA")
  private String fieldInA;
}

class B extends A {
  @SerializedName("fieldInB")
  private String fieldInB;
}
```

```
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
```


Both the use of `transient` and the use of the annotation `SerializedName` allow
the fields to be renamed by R8 to the same name, but GSON serialization will
work as expected.

# R8 full mode

In full mode, R8 performs more aggressive optimizations, meaning that additional
ProGuard configuration rules may be required. This section highlights some
common issues that have been seen when using full mode.

## Retrofit

### Object instantiated with Retrofit's `create()` method is always replaced with `null`

This happens because Retrofit uses reflection to return an object that
implements a given interface. The issue can be resolved by using the most recent
keep rules from the Retrofit library.

See also https://github.com/square/retrofit/issues/3005 ("Insufficient keep
rules for R8 in full mode").
