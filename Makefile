SHELL := /bin/bash
.DELETE_ON_ERROR:

DATE=`date +%Y%m%d`

# for printing variable values
# usage: make print-VARIABLE
#        > VARIABLE = value_of_variable
print-%  : ; @echo $* = $($*)

# literal space
space :=
space +=

# Decide OS-specific questions
# jar-file seperator
ifeq ($(OS),Windows_NT)
  SEP = ;
else
# linux
  UNAME = $(shell uname)
  ifeq ($(UNAME),Darwin)
    SEP = :
  endif
  ifeq ($(UNAME),Linux)
    SEP = :
  endif
endif

# Fun Args to javac.  Mostly limit to java8 source definitions, and fairly
# agressive lint warnings.
JAVAC_ARGS = -g -source 1.8 -target 1.8 -XDignore.symbol.file -Xlint:all -Xlint:-deprecation -Xlint:-serial -Xlint:-rawtypes -Xlint:-unchecked

# Source/Test code
SRC = src/main/java/embeddings
TST = src/test/java/embeddings
main_javas = $(wildcard $(SRC)/*java)
test_javas = $(wildcard $(TST)/*java)
main_classes = $(patsubst $(SRC)/%java,build/classes/main/%class,$(main_javas))
test_classes = $(patsubst $(TST)/%java,build/classes/test/%class,$(test_javas))
classes = $(main_classes) $(test_classes)
javas = $(main_javas)

# Just build the jar file
default: build/faster_em.jar

# run all junits
test: build/faster_em.jar  build/faster_em-test.jar
	./junits.sh

# Compile just the out-of-date files
$(main_classes): build/classes/main/%class: $(SRC)/%java
	@echo "compiling " $@ " because " $?
	@[ -d build/classes/main ] || mkdir -p build/classes/main
	@javac $(JAVAC_ARGS) -cp "build/classes/main$(SEP)" -sourcepath $(SRC) -d build/classes/main $(javas)

$(test_classes): build/classes/test/%class: $(TST)/%java
	@echo "compiling " $@ " because " $?
	@[ -d build/classes/teset ] || mkdir -p build/classes/test
	@javac $(JAVAC_ARGS) -cp "build/classes/main$(SEP)build/classes/test$(SEP)lib/*" -sourcepath $(TST) -d build/classes/test $(test_javas)

build/faster_em.jar: $(main_classes)
	@echo "  jarring " $@ " because " $?
	@[ -d build ] || mkdir -p build
	@jar -cf build/faster_em.jar -C build/classes/main .

build/faster_em-test.jar: $(test_classes)
	@echo "jarring " $@ " because " $?
	@[ -d build ] || mkdir -p build
	@jar -cf build/faster_em-test.jar -C build/classes/test .

.PHONY: clean
clean:
	rm -rf build
