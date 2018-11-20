#!/usr/bin/env bash
echo ${TEST_URL}
zap-api-scan.py -t ${TEST_URL}/v2/api-docs -f openapi -r api-report.html
cp *.html functional-output/
zap-cli -p 1001 alerts -l Informational
