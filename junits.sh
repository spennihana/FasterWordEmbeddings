#!/bin/bash

# Clean out any old sandboxes, make a new one
OUTDIR=sandbox
rm -fr $OUTDIR; mkdir -p $OUTDIR

# Check for os
SEP=:
case "`uname`" in
    CYGWIN* )
      SEP=";"
      ;;
esac

# Run cleanup on interrupt or exit
function cleanup () {
  kill -9 ${PID_1} ${PID_2} ${PID_3} ${PID_4} 1> /dev/null 2>&1
  wait 1> /dev/null 2>&1
  RC=`cat $OUTDIR/status.0`
  if [ $RC -ne 0 ]; then
    cat $OUTDIR/out.0
    echo junit tests FAILED
  else
    echo junit tests PASSED
  fi
  exit $RC
}
trap cleanup SIGTERM SIGINT

# Find java command
if [ -z "$TEST_JAVA_HOME" ]; then
  # Use default
  JAVA_CMD="java"
else
  # Use test java home
  JAVA_CMD="$TEST_JAVA_HOME/bin/java"
fi

MAX_MEM="-Xmx15g -Xms15g -XX:+PrintGC"

# Command to invoke test.
JVM="nice $JAVA_CMD $MAX_MEM -ea -cp build/*${SEP}lib/*"
echo "$JVM" > $OUTDIR/jvm_cmd.txt

# Runner
JUNIT_RUNNER="org.junit.runner.JUnitCore"

# find all java in the src/test directory
# add 'sort' to get determinism on order of tests on different machines
# methods within a class can still reorder due to junit?
# '/usr/bin/sort' needed to avoid windows native sort when run in cygwin
(cd src/test/java; /usr/bin/find . -name '*.java' | cut -c3- | sed 's/.....$//' | sed -e 's/\//./g') | grep -v ComparisonUtils | /usr/bin/sort > $OUTDIR/tests.txt

# Launch last driver JVM.  All output redir'd at the OS level to sandbox files.
echo Running junits...
while read p; do
  echo Running test $p
  ($JVM $JUNIT_RUNNER $p 2>&1 ; echo $? > $OUTDIR/status.0) 1>> $OUTDIR/out.0 2>&1
done < $OUTDIR/tests.txt

grep EXECUTION $OUTDIR/out.0 | cut "-d " -f23,20 | awk '{print $2 " " $1}'| sort -gr | head -n 10 >> $OUTDIR/out.0

cleanup

