.PHONY: build compile package clean

build: compile package

# -cp is not needed since all classes are passed to the compiler
compile:
	mkdir -p build
	javac src/main/java/chorddht/*.java -d build

package:
	jar -cvfm build/chorddht.jar src/main/resources/META-INF/MANIFEST.MF -C build/ .

clean:
	rm -rf build
