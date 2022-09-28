# R8 FAQ

R8 uses the same configuration specification language as ProGuard, and tries to
be compatible with ProGuard. However as R8 has different optimizations it can be
necessary to change the configuration when switching to R8. R8 provides two
modes, R8 compatibility mode and R8 full mode. R8 compatibility mode is default
in Android Studio and is meant to make the transition to R8 from ProGuard easier
by limiting the optimizations performed by R8.

## R8 full mode
In non-compat mode, also called “full mode”, R8 performs more aggressive
optimizations, meaning additional ProGuard configuration rules may be required.
Full mode can be enabled by adding `android.enableR8.fullMode=true` in the
`gradle.properties` file. The main differences compared to R8 compatibility mode
are:

- The default constructor (`<init>()`) is not implicitly kept when a class is
kept.
- The default constructor (`<init>()`) is not implicitly kept for types which
are only used with `ldc`, `instanceof` or `checkcast`.
- The enclosing classes of fields or methods that are matched by a
`-keepclassmembers` rule are not implicitly considered to be instantiated.
Classes that are only instantiated using reflection should be kept explicitly
with a `-keep` rule.
- Default methods are not implicitly kept as abstract methods.
- Attributes (such as `Signature`) and annotations are only kept for classes,
methods and fields which are matched by keep rules even when `-keepattributes`
is specified.
Additionally, for attributes describing a relationship such as `InnerClass` and
`EnclosingMethod`, non-compat mode requires both endpoints being kept.

# Troubleshooting

The rest of this document describes known issues with libraries that use
reflection.

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
several classes in a class hierarchy. Consider the following example:

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
used to fix the `IllegalArgumentException` together with the rule to keep fields
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

### GSON

GSON uses type tokens to serialize and deserialize generic types.

```TypeToken<List<String>> listOfStrings = new TypeToken<List<String>>() {};```

The anonymous class will have a generic signature argument of `List<String>` to
the super type `TypeToken` that is reflective read for serialization. It
is therefore necessary to keep both the `Signature` attribute, the
`com.google.gson.reflect.TypeToken` class and all sub-types.

```
-keepattributes Signature
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
```

This is also needed for R8 in compat mode since multiple optimizations will
remove the generic signature such as class merging and argument removal.

## Retrofit

### Objects instantiated with Retrofit's `create()` method are always replaced with `null`

This happens because Retrofit uses reflection to return an object that
implements a given interface. The issue can be resolved by using the most recent
keep rules from the Retrofit library.

See also https://github.com/square/retrofit/issues/3005 ("Insufficient keep
rules for R8 in full mode").

### Kotlin suspend functions and generic signatures

For Kotlin suspend functions the generic signature is reflectively read.
Therefore keeping the `Signature` attribute is necessary. Full mode only keeps
the signature for kept classes thus a keep on `kotlin.coroutines.Continuation` in
addition to a keep on the api classes is needed:
```
-keepattributes Signature
-keep class kotlin.coroutines.Continuation
```

This should be included automatically from versions built after the pull-request
https://github.com/square/retrofit/pull/3563

