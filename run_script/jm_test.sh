#!/bin/bash
rm -rf jmeter_report/*
rm test.jsl
jmeter -n -t test.jmx -l test.jsl -e -o jmeter_report
