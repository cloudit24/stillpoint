# Manifest components are kept by R8 automatically.
# The assistant's native library calls these by name.
-keep class com.cloudit24.stillpoint.llm.LlmNative { *; }
-keep class com.cloudit24.stillpoint.llm.LlmNative$Sink { *; }
-keepclassmembers class * implements com.cloudit24.stillpoint.llm.LlmNative$Sink { public boolean onToken(byte[]); }
