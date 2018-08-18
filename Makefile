.PHONY: build compile package clean

build: compile package

compile:
	mkdir -p build
	javac chorddht/*.java -d build

package:
	jar -cvfm build/chorddht.jar MANIFEST.MF build/chorddht

clean:
	rm -rf build
