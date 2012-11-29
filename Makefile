# Configuration
# =============
# Build information
APPNAME = 'JStork'
VERSION = '0.0.1 (really alpha)'


# =============
PROJECT = stork
PACKAGES = stork stork/util stork/module stork/stat

CLASSPATH = '.:lib/EXTRACTED/:build'
JFLAGS = -J-Xmx512m -g -cp $(CLASSPATH) -verbose -Xlint:unchecked
JC = javac
JAVA = java
JAR = jar -J-Xmx512m

.PHONY: all install clean dist-clean init release
.SUFFIXES: .java .class

JAVASRCS = $(wildcard $(PACKAGES:%=%/*.java))
CLASSES = $(JAVASRCS:%.java=build/%.class)

all: init lib/EXTRACTED $(CLASSES) build/build_tag $(PROJECT).jar

build:
	mkdir -p build

$(PROJECT).jar: $(CLASSES)
	$(JAR) cf $(PROJECT).jar -C build . -C lib/EXTRACTED .
	cp $(PROJECT).jar bin/

build/%.class: %.java | build
	$(JC) $(JFLAGS) -d build $<

init: | build

release: $(PROJECT).tar.gz

src-release: $(PROJECT)-src.tar.gz

$(PROJECT).tar.gz: $(PROJECT).jar
	cp $(PROJECT).jar bin/
	tar czf $(PROJECT).tar.gz bin libexec --exclude='*/CVS' \
		--transform 's,^,$(PROJECT)/,'

$(PROJECT)-src.tar.gz: dist-clean
	tar czf $(PROJECT)-src.tar.gz * --exclude='*/CVS'

# FIXME: This is a bad hack.
lib/EXTRACTED:
	cd lib && ./extract.sh

build/build_tag: $(CLASSES) | build
	@echo generating build tag
	@echo appname = $(APPNAME) >  build/build_tag
	@echo version = $(VERSION) >> build/build_tag
	@echo buildtime = `date`   >> build/build_tag

clean:
	$(RM) -rf build $(PROJECT).jar $(PROJECT).tar.gz

dist-clean: clean
	$(RM) -rf lib/EXTRACTED
