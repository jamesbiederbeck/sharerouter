APP_ID = com.example.sharerouter

SRC := $(shell find src -name "*.java")

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

all: $(ALIGNED)

$(R8_JAR):
	mkdir -p $(TOOLS)
	curl -sL $(R8_URL) -o $@
	echo "$(R8_SHA256)  $@" | sha256sum -c -

$(DEX): $(SRC) $(LIBS) $(R8_JAR)
	rm -rf $(OUT)
	mkdir -p $(CLASSES)
	javac -source 8 -target 8 -classpath $(ANDROID_JAR):$(LIBS_CP) -d $(CLASSES) $(SRC)
	$(D8) --lib $(ANDROID_JAR) --min-api 24 --output $(OUT) $$(find $(CLASSES) -name "*.class") $(LIBS)

RES := $(shell find res -type f)

$(RESZIP): $(RES)
	mkdir -p $(OUT)
	$(AAPT2) compile --dir res -o $(RESZIP)

$(UNSIGNED): $(DEX) $(RESZIP) AndroidManifest.xml
	$(AAPT2) link \
		-I $(ANDROID_JAR) \
		--manifest AndroidManifest.xml \
		--min-sdk-version 24 \
		--target-sdk-version 33 \
		$(RESZIP) \
		-o $(UNSIGNED)
	zip -j -u $(UNSIGNED) $(OUT)/classes*.dex

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
	rm -rf out *.apk
