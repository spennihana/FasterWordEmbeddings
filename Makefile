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

# Source code
# Note that BuildVersion is not forced to be rebuilt here - so incremental
# makes in this directory will endlessly use the same BuildVersion.
SRC = src/main/java/embeddings
main_javas = $(wildcard $(SRC)/*java)
main_classes = $(patsubst $(SRC)/%java,build/classes/main/%class,$(main_javas))
javas = $(main_javas)

# Just build the jar file
default: build/faster_em.jar


# Compile just the out-of-date files
$(main_classes): build/classes/main/%class: $(SRC)/%java
	@echo "compiling " $@ " because " $?
	@[ -d build/classes/main ] || mkdir -p build/classes/main
	@javac $(JAVAC_ARGS) -cp "build/classes/main$(SEP)" -sourcepath $(SRC) -d build/classes/main $(javas)

build/faster_em.jar: $(main_classes)
	@echo "  jarring " $@ " because " $?
	@[ -d build ] || mkdir -p build
	@jar -cf build/faster_em.jar -C build/classes/main .

.PHONY: clean
clean:
	rm -rf build
