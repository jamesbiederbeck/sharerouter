APP_ID = com.example.sharerouter

SRC := $(shell find src -name "*.java")

OUT = out
CLASSES = $(OUT)/classes
DEX = $(OUT)/classes.dex
RESZIP = $(OUT)/compiled_res.zip

UNSIGNED = unsigned.apk
ALIGNED = aligned.apk

ANDROID_JAR ?= /usr/lib/android-sdk/platforms/android-33/android.jar
AAPT2 ?= /usr/lib/android-sdk/build-tools/33.0.1/aapt2

KEYSTORE = debug.keystore
DEVICE ?= $(or $(ANDROID_DEVICE),$(shell $(ADB) devices | awk 'NR>1 && $$2=="device" {print $$1; exit}'))
ADB = ../platform-tools/adb

all: $(ALIGNED)

$(DEX): $(SRC)
	rm -rf $(OUT)
	mkdir -p $(CLASSES)
	javac -source 8 -target 8 -classpath $(ANDROID_JAR) -d $(CLASSES) $(SRC)
	d8 --lib $(ANDROID_JAR) --output $(OUT) $$(find $(CLASSES) -name "*.class")

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
	zip -j -u $(UNSIGNED) $(DEX)

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
