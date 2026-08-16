APP_ID = com.example.sharerouter

# PAW=1 (default): full build with native llama.cpp/PAW inference support.
# PAW=0: minimal build — PawActivity/LlamaBridge excluded from compilation
# entirely (not just missing native libs), the PawActivity manifest entry
# stripped, and the hamburger menu's "PAW Inference" item hidden
# (BuildConfig.PAW_ENABLED, see $(BUILD_CONFIG) below).
PAW ?= 1

ALL_SRC := $(shell find src -name "*.java")
ifeq ($(PAW),1)
SRC := $(ALL_SRC)
else
SRC := $(filter-out src/com/example/sharerouter/PawActivity.java src/com/example/sharerouter/LlamaBridge.java src/com/example/sharerouter/LicensesActivity.java,$(ALL_SRC))
endif

LIBS := libs/javascriptengine-1.1.0.jar \
        libs/androidx-core-slice.jar \
        libs/concurrent-futures-1.0.0.jar \
        libs/annotation-1.8.1.jar \
        libs/guava-32.0.1-android.jar
LIBS_CP := $(shell echo $(LIBS) | tr ' ' ':')

OUT = out
CLASSES = $(OUT)/classes
DEX = $(OUT)/classes.dex
RESZIP = $(OUT)/compiled_res.zip

GEN_DIR = $(OUT)/gen
BUILD_CONFIG = $(GEN_DIR)/com/example/sharerouter/BuildConfig.java
PAW_ENABLED = $(if $(filter 1,$(PAW)),true,false)

UNSIGNED = unsigned.apk
ALIGNED = aligned.apk

ANDROID_JAR ?= /usr/lib/android-sdk/platforms/android-33/android.jar
AAPT2 ?= /usr/lib/android-sdk/build-tools/33.0.1/aapt2

# The system d8 (build-tools 33.0.1 and 34.0.0 both tested) crashes with an
# NPE dexing androidx.javascriptengine 1.1.0's classes: a MethodParameters
# attribute with a null-named synthetic parameter, which is legal per the
# JVM spec but trips D8's parser. Verified fixed in standalone R8 8.3.37, so
# we invoke that directly instead of the SDK's bundled d8 binary.
R8_URL = https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.3.37/r8-8.3.37.jar
R8_SHA256 = 59753e70a74f918389cc87f1b7d66b5c0862932559167425708ded159e3de439
TOOLS = tools
R8_JAR = $(TOOLS)/r8-8.3.37.jar
D8 = java -cp $(R8_JAR) com.android.tools.r8.D8

KEYSTORE = debug.keystore
DEVICE ?= $(or $(ANDROID_DEVICE),$(shell $(ADB) devices | awk 'NR>1 && $$2=="device" {print $$1; exit}'))
ADB = ../platform-tools/adb

# Native llama.cpp JNI shim (arm64-v8a only — this device's only ABI).
# LLAMA_CPP_DIR must point at a llama.cpp checkout already CMake-built for
# arm64-v8a (see jni/CMakeLists.txt); that build is out-of-tree and not
# reproduced here since llama.cpp is not vendored into this repo.
NDK ?= /usr/lib/android-ndk
LLVM_STRIP = $(NDK)/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip
LLAMA_CPP_DIR ?= $(HOME)/code/ml/llama.cpp
LLAMA_CPP_BUILD_DIR = $(LLAMA_CPP_DIR)/build-android
NATIVE_ABI = arm64-v8a
JNI_DIR = jni
JNI_BUILD_DIR = $(JNI_DIR)/build
NATIVE_LIBS_DIR = $(OUT)/lib/$(NATIVE_ABI)
NATIVE_STAMP = $(OUT)/.native_libs_stamp

all: $(ALIGNED)

$(R8_JAR):
	mkdir -p $(TOOLS)
	curl -sL $(R8_URL) -o $@
	echo "$(R8_SHA256)  $@" | sha256sum -c -

# Regenerated every build (cheap) but only touched if the content actually
# changes, so switching PAW back and forth doesn't force a needless rebuild
# when it ends up back at the same value.
.PHONY: $(BUILD_CONFIG)
$(BUILD_CONFIG):
	mkdir -p $(dir $@)
	echo 'package com.example.sharerouter; final class BuildConfig { static final boolean PAW_ENABLED = $(PAW_ENABLED); private BuildConfig() {} }' > $@.tmp
	cmp -s $@.tmp $@ 2>/dev/null && rm $@.tmp || mv $@.tmp $@

# Only clears its own outputs (not the whole $(OUT) dir) so it doesn't race
# with $(NATIVE_STAMP)/$(RESZIP), which also live under $(OUT).
$(DEX): $(SRC) $(BUILD_CONFIG) $(LIBS) $(R8_JAR)
	rm -rf $(CLASSES) $(OUT)/classes*.dex
	mkdir -p $(CLASSES)
	javac -source 8 -target 8 -classpath $(ANDROID_JAR):$(LIBS_CP) -d $(CLASSES) $(SRC) $(BUILD_CONFIG)
	$(D8) --lib $(ANDROID_JAR) --min-api 24 --output $(OUT) $$(find $(CLASSES) -name "*.class") $(LIBS)

RES := $(shell find res -type f)
ifeq ($(PAW),1)
ASSETS := $(shell find assets -type f)
else
ASSETS :=
endif

$(RESZIP): $(RES)
	mkdir -p $(OUT)
	$(AAPT2) compile --dir res -o $(RESZIP)

# For PAW=0, strips the <!-- PAW:BEGIN --> ... <!-- PAW:END --> block (the
# PawActivity <activity> entry) out of the manifest, rather than hand
# maintaining a second full manifest that could drift.
#
# .PHONY (like $(BUILD_CONFIG)) because the output depends on $(PAW), not
# just AndroidManifest.xml's mtime — a plain file-mtime rule would leave a
# stale manifest from a previous PAW=0/1 run untouched by a later run with
# the other value, since the real source file never changed.
GEN_MANIFEST = $(OUT)/AndroidManifest.xml
.PHONY: $(GEN_MANIFEST)
$(GEN_MANIFEST): AndroidManifest.xml
	mkdir -p $(OUT)
ifeq ($(PAW),1)
	cp AndroidManifest.xml $@.tmp
else
	sed '/<!-- PAW:BEGIN -->/,/<!-- PAW:END -->/d' AndroidManifest.xml > $@.tmp
endif
	cmp -s $@.tmp $@ 2>/dev/null && rm $@.tmp || mv $@.tmp $@

$(NATIVE_STAMP): $(JNI_DIR)/llama_jni.cpp $(JNI_DIR)/CMakeLists.txt
	cmake -B $(JNI_BUILD_DIR) -G Ninja -S $(JNI_DIR) \
		-DCMAKE_TOOLCHAIN_FILE=$(NDK)/build/cmake/android.toolchain.cmake \
		-DANDROID_ABI=$(NATIVE_ABI) \
		-DANDROID_PLATFORM=android-24 \
		-DCMAKE_BUILD_TYPE=Release \
		-DLLAMA_CPP_DIR=$(LLAMA_CPP_DIR)
	ninja -C $(JNI_BUILD_DIR)
	mkdir -p $(NATIVE_LIBS_DIR)
	cp $(JNI_BUILD_DIR)/libllama_jni.so $(NATIVE_LIBS_DIR)/
	cp $(LLAMA_CPP_BUILD_DIR)/bin/libllama.so $(NATIVE_LIBS_DIR)/
	cp $(LLAMA_CPP_BUILD_DIR)/bin/libggml.so $(NATIVE_LIBS_DIR)/
	cp $(LLAMA_CPP_BUILD_DIR)/bin/libggml-base.so $(NATIVE_LIBS_DIR)/
	cp $(LLAMA_CPP_BUILD_DIR)/bin/libggml-cpu.so $(NATIVE_LIBS_DIR)/
	$(LLVM_STRIP) $(NATIVE_LIBS_DIR)/*.so
	touch $@

ifeq ($(PAW),1)
NATIVE_DEP = $(NATIVE_STAMP)
ASSET_FLAG = -A assets
NATIVE_ZIP_CMD = (cd $(OUT) && zip -u ../$(UNSIGNED) lib/$(NATIVE_ABI)/*.so)
else
NATIVE_DEP =
ASSET_FLAG =
NATIVE_ZIP_CMD = true
endif

$(UNSIGNED): $(DEX) $(RESZIP) $(NATIVE_DEP) $(ASSETS) $(GEN_MANIFEST)
	$(AAPT2) link \
		-I $(ANDROID_JAR) \
		--manifest $(GEN_MANIFEST) \
		--min-sdk-version 24 \
		--target-sdk-version 33 \
		$(ASSET_FLAG) \
		$(RESZIP) \
		-o $(UNSIGNED)
	zip -j -u $(UNSIGNED) $(OUT)/classes*.dex
	$(NATIVE_ZIP_CMD)

$(ALIGNED): $(UNSIGNED)
	rm -f $(ALIGNED)
	zipalign -p 4 $(UNSIGNED) $(ALIGNED)
	apksigner sign \
		--ks $(KEYSTORE) \
		--ks-pass pass:android \
		--key-pass pass:android \
		$(ALIGNED)

install: $(ALIGNED)
	$(ADB) connect $(DEVICE)
	$(ADB) -s $(DEVICE) install -r $(ALIGNED)

run: install
	$(ADB) -s $(DEVICE) shell am start -n $(APP_ID)/.MainActivity

keystore:
	keytool -genkeypair \
		-keystore $(KEYSTORE) \
		-alias androiddebugkey \
		-keyalg RSA \
		-keysize 2048 \
		-validity 10000 \
		-storepass android \
		-keypass android \
		-dname "CN=Android Debug,O=Android,C=US"

clean:
	rm -rf out *.apk $(JNI_BUILD_DIR)
