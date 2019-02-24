# Loggerator

[![Build Status](https://travis-ci.org/Tandolf/Loggerator.svg?branch=master)](https://travis-ci.org/Tandolf/Loggerator)

Log transactions in JSON format for easy access in tools like splunk.

## Spring properties
All properties should be prefixed with "loggerator." 

| Property                              | Default           | Description  |
| :-------------                        |:-------------     | :-----|
| pretty-print               | false             | Will print logs in pretty print format in the console |
| filter.active                     | true              | Activate logging of incoming requests |
| filter.include-payload            | false             | Will log incoming payloads if filter is active |
| filter.include-query-string       | false             | Will append and log the query string ontop of incoming url |
| filter.max-payload-length         | 4096              | Set max length of the payload to log (bytes) |