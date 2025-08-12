APP_NAME=azmagall
MAIN_CLASS=AzmaGall

.PHONY: all native clean

all: native

native:
	@if ! command -v native-image >/dev/null 2>&1; then \
		echo "❌ native-image not found. Please install GraalVM and add native-image to PATH."; \
		exit 1; \
	fi
	javac $(MAIN_CLASS).java
	native-image --no-fallback -O3 -H:Name=$(APP_NAME) $(MAIN_CLASS)

clean:
	rm -f $(APP_NAME) *.class
